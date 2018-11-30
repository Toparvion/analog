package tech.toparvion.analog.remote.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import tech.toparvion.analog.model.ServerFailure;
import tech.toparvion.analog.model.TrackingRequest;
import tech.toparvion.analog.model.config.ChoiceProperties;
import tech.toparvion.analog.model.config.ClusterNode;
import tech.toparvion.analog.model.config.ClusterProperties;
import tech.toparvion.analog.model.config.LogConfigEntry;
import tech.toparvion.analog.remote.server.RegistrationChannelCreator;
import tech.toparvion.analog.remote.server.RemoteGateway;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.util.AnaLogUtils.*;
import static tech.toparvion.analog.util.PathConstants.*;

/**
 * A component responsible for supporting lifecycle of websocket sessions and watching subscriptions.
 * Built as listener of websocket-specific application events.
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class WebSocketEventListener {
  private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

  private final ChoiceProperties choiceProperties;
  private final WatchRegistry registry;
  private final RegistrationChannelCreator registrationChannelCreator;
  private final ClusterProperties clusterProperties;
  private final RemoteGateway remoteGateway;
  private final SimpMessagingTemplate messagingTemplate;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")    // IDEA doesn't see remoteGateway
  public WebSocketEventListener(ChoiceProperties choiceProperties,
                                WatchRegistry registry,
                                RegistrationChannelCreator registrationChannelCreator,
                                ClusterProperties clusterProperties,
                                RemoteGateway remoteGateway,
                                SimpMessagingTemplate messagingTemplate) {
    this.choiceProperties = choiceProperties;
    this.registry = registry;
    this.registrationChannelCreator = registrationChannelCreator;
    this.clusterProperties = clusterProperties;
    this.remoteGateway = remoteGateway;
    this.messagingTemplate = messagingTemplate;
  }

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
    log.info("New websocket session established with id={}", headers.getSessionId());
  }

  @EventListener
  public void onSubscribe(SessionSubscribeEvent event) {
    // First let's extract all the necessary info about new watching from the subscribe request
    StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
    boolean isPlain = getBooleanNativeHeader(headers, "isPlain");
    boolean isTailNeeded = getBooleanNativeHeader(headers, "isTailNeeded");
    String sessionId = headers.getSessionId();
    String destination = headers.getDestination();
    assert (destination != null) && destination.startsWith(WEBSOCKET_TOPIC_PREFIX)
        : "Subscriber's 'destination' header is absent or malformed: " + destination;
    log.debug("Received start watching (SUBSCRIBE) command for {} destination '{}' within session id={}.",
        (isPlain ? "plain" : "composite"), destination, sessionId);

    // Then create or find corresponding log config entry
    destination = destination.replace(WEBSOCKET_TOPIC_PREFIX, "");
    LogConfigEntry logConfig = isPlain
        ? createPlainLogConfigEntry(destination)
        : findCompositeLogConfigEntry(destination);

    // Find out whether this log is already being watched by any client(s)
    List<String> watchingSessionIds = registry.findWatchingSessionsFor(logConfig);
    if (watchingSessionIds == null) {
      try {
        log.debug("No watching existed for {} log '{}' before. Starting new one...", (isPlain ? "plain" : "composite"),
            logConfig.getUid());
        // 1. Ensure that all RMI registration channels are created
        ensureRegistrationChannelsCreated(logConfig);
        // 2. Register the tracking on specified nodes
        startTrackingOnServer(logConfig, destination, isTailNeeded);
        // 3. Remember the tracking in the registry
        registry.addEntry(logConfig, sessionId);
        log.info("New tracking for log '{}' has started within session id={}.", logConfig.getUid(), sessionId);

      } catch (Exception e) {
        log.error(format("Failed to start watching of log '%s'.", logConfig.getUid()), e);
        ServerFailure failure = new ServerFailure(e.getMessage(), now());
        messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + logConfig.getUid(),
            failure, singletonMap(MESSAGE_TYPE_HEADER, MessageType.FAILURE));
        // TODO Научиться по аналогии с этим отправлять сообщение об успешной настройке подписки, для чего превратить
        // serverFailure в более общий тип сообщения. При получении этого типа убирать на клиенте прелоадер,
        // выставленный перед отправкой запроса на подписку.
      }

    } else {    // i.e. if there are watching sessions already in registry
      assert !watchingSessionIds.contains(sessionId)
          : format("Session id=%s is already watching log '%s'. Double subscription is prohibited.",
          sessionId, logConfig.getUid());
      watchingSessionIds.add(sessionId);
      log.info("There were {} session(s) already watching log '{}'. New session {} has been added to them.",
          watchingSessionIds.size()-1, logConfig.getUid(), sessionId);
    }
  }

  @EventListener
  public void onUnsubscribe(SessionUnsubscribeEvent event) {
    log.info("Received UNSUBSCRIBE event.");
    stopTrackingIfNeeded(event.getMessage(), true);
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    log.info("Received DISCONNECT event.");
    stopTrackingIfNeeded(event.getMessage(), false);
  }

  private void stopTrackingIfNeeded(Message<byte[]> message, boolean isUnsubscribing) {
    StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
    String sessionId = headers.getSessionId();
    log.debug("Unsubscribing session with id={}...", sessionId);
    // find all sessions watching the same log (including current one)
    List<String> fellows = registry.findFellowsFor(sessionId);
    // check if there is no such session(s)
    if (fellows == null) {
      if (isUnsubscribing) {      // in case of unsubscribing it is incorrect situation
        log.warn("No registered session(s) found for sessionId={}", sessionId);
      } else {                    // but in case of disconnecting it is quite right
        log.info("No registered session found for sessionId={}.", sessionId);
      }
      return;
    }
    // check if there any other session(s) left (may require synchronization on registry object)
    if (fellows.size() > 1) {
      fellows.remove(sessionId);
      log.info("There are still {} session(s) watching the same log as removed one (sessionId={}). " +
          "Will keep the tracking active.", fellows.size(), sessionId);
      return;
    }
    // in case it was the latest session watching that log we should stop the tracking
    LogConfigEntry watchingLog = registry.findLogConfigEntryBy(sessionId);
    log.debug("No sessions left watching log '{}'. Will deactivate the tracking...", watchingLog.getUid());
    doSafely(getClass(), () -> stopTrackingOnServer(watchingLog));
    // now that the log is not tracked anymore we need to remove it from the registry
    registry.removeEntry(watchingLog);
    log.info("Current node has unregistered itself from tracking log '{}' as there is no watching sessions anymore.",
            watchingLog.getUid());
  }

  private boolean getBooleanNativeHeader(StompHeaderAccessor headers, String name) {
    List<String> rawHeaderValue = headers.getNativeHeader(name);
    assert (rawHeaderValue != null) && (rawHeaderValue.size() == 1)
        : format("'%s' header of SUBSCRIBE command is absent or malformed", name);
    return Boolean.valueOf(rawHeaderValue.get(0));
  }

  /**
   * When watching request of web client comes for a plain log, AnaLog doesn't search for corresponding log config entry.
   * Instead it just creates new ('artificial') entry and then works with it only. This approach allows AnaLog to
   * watch arbitrary plain logs independently of its configuration. Particularly, it means that a user can set any
   * path into AnaLog's address line and start to watch it the same way as if it was pre-configured as a choice
   * variant in configuration file.
   *
   * @param path full path of log file to watch for
   * @return newly created log config entry for the specified path
   */
  @NotNull
  /*private*/ LogConfigEntry createPlainLogConfigEntry(String path) {
    // first, let's remove the leading 'artificial' slash (added by frontend) from the path
    String unleadedPath = removeLeadingSlash(path);
    String cleanPath, detectedNode;
    if (isLocalFilePath(unleadedPath)) {
      cleanPath = unleadedPath;
      detectedNode = clusterProperties.getMyselfNode().getName();

    } else if (unleadedPath.startsWith(NODE_PATH_PREFIX)) {
      int prefixIndex = unleadedPath.indexOf(CUSTOM_SCHEMA_SEPARATOR);
      var nodefulPath = unleadedPath.substring(prefixIndex + CUSTOM_SCHEMA_SEPARATOR.length());
      int nodeSeparatorIndex = nodefulPath.indexOf('/');
      detectedNode = nodefulPath.substring(0, nodeSeparatorIndex);
      cleanPath = nodefulPath.substring(nodeSeparatorIndex + 1);

    } else if (unleadedPath.startsWith(COMPOSITE_PATH_PREFIX)) {
      throw new IllegalArgumentException(format("Composite log path '%s' must not be passed " +
          "to plain log path processing.", unleadedPath));

    } else {
      cleanPath = unleadedPath;
      detectedNode = clusterProperties.getMyselfNode().getName();

    }
    String title = extractFileName(cleanPath);

    LogConfigEntry artificialEntry = new LogConfigEntry();
    artificialEntry.setPath(cleanPath);
    artificialEntry.setNode(detectedNode);
    artificialEntry.setTitle(title);
    log.debug("For path  '{}' new plain log config entry created with cleanPath='{}', node='{}' and title='{}'",
        path, cleanPath, detectedNode, title);
    return artificialEntry;
  }

  @NotNull
  private LogConfigEntry findCompositeLogConfigEntry(String uid) {
    List<LogConfigEntry> matchingEntries = choiceProperties.getChoices().stream()
            .flatMap(choiceGroup -> choiceGroup.getCompositeLogs().stream())
            .filter(entry -> entry.getUid().equals(uid))
            .collect(toList());
    if (matchingEntries.isEmpty()) {
      throw new IllegalArgumentException(format("No log configuration entry found for uid=%s", uid));
    }
    LogConfigEntry matchingEntry;
    if (matchingEntries.size() > 1) {
      log.warn("Multiple matching entries found for uid={}. Will pick the first one.\n{}", uid, matchingEntries);
    }
    matchingEntry = matchingEntries.get(0);
    log.debug("Found matching composite log config entry: {}", matchingEntry);
    return matchingEntry;
  }

  private void ensureRegistrationChannelsCreated(LogConfigEntry matchingEntry) {
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    // if main log entry points to a node other than current one then we need to create registration channel to it
    if ((matchingEntry.getNode() != null) && !myselfNode.getName().equals(matchingEntry.getNode())) {
      registrationChannelCreator.createRegistrationChannelIfNeeded(matchingEntry.getNode());
    }
    matchingEntry.getIncludes().stream()
        .filter(entry -> entry.getNode() != null)
        .forEach(entry -> {
          registrationChannelCreator.createRegistrationChannelIfNeeded(entry.getNode());
          // additionally warn the administrator if this entry contains other includes
          if (!entry.getIncludes().isEmpty()) {
            log.warn("Encountered included log config entry that itself contains other included entries. Nested " +
                "inclusion levels deeper than 2 are not supported and will be ignored. Invalid entry: {}", entry);
          }
        });
  }

  private void startTrackingOnServer(LogConfigEntry logConfigEntry, String destination, boolean isTailNeeded) {
    switchTrackingOnServer(logConfigEntry, destination, true, isTailNeeded);
  }

  private void stopTrackingOnServer(LogConfigEntry logConfigEntry) {
    switchTrackingOnServer(logConfigEntry, null, false, false);
  }

  private void switchTrackingOnServer(LogConfigEntry logConfigEntry, @Nullable String destination, boolean isOn,
                                      boolean isTailNeeded) {
    assert !(isTailNeeded && !isOn) : "isTailNeeded flag should not be raised when switching the tracking off";
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    String nodeName = nvls(logConfigEntry.getNode(), myselfNode.getName());

    // send registration request for the main entry
    TrackingRequest primaryRequest = new TrackingRequest(
        convertToUnixStyle(logConfigEntry.getPath(), false),
        logConfigEntry.getTimestamp(),
        nodeName,
        destination,
        isTailNeeded);
    log.debug("Switching {} the registration by PRIMARY request: {}", isOn ? "ON":"OFF", primaryRequest);
    remoteGateway.switchRegistration(primaryRequest, isOn);

    // send registration requests for included entries
    for (LogConfigEntry included : logConfigEntry.getIncludes()) {
      TrackingRequest includedRequest = null;
      try {
        includedRequest = new TrackingRequest(
            convertToUnixStyle(included.getPath(), false),
            included.getTimestamp(),
            nvls(included.getNode(), myselfNode.getName()),
            destination,
            isTailNeeded);
        log.debug("Switching {} the registration by INCLUDED request: {}", isOn ? "ON":"OFF", includedRequest);
        remoteGateway.switchRegistration(includedRequest, isOn);

      } catch (Exception e) {
        log.error(format("Failed to switch %s the registration by included request: %s",
            isOn ? "ON":"OFF", (includedRequest==null) ? "n/a" : includedRequest), e);
        // TODO inform the user about this partial failure by sending ServerFault notification
      }
    }
  }

}
