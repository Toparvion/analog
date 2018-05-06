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
import tech.toparvion.analog.model.config.*;
import tech.toparvion.analog.remote.server.RegistrationChannelCreator;
import tech.toparvion.analog.remote.server.RemoteGateway;

import javax.validation.constraints.NotNull;
import java.util.List;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonMap;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.service.AnaLogUtils.*;

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
        (isPlain?"plain":"composite"), destination, sessionId);

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
        startTrackingOnServer(logConfig, isTailNeeded);
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
    stopTrackingOnServer(watchingLog);
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
   * When watching request comes for a plain log AnaLog does not tries to find corresponding log config entry.
   * Instead it just creates new ('artificial') entry and then works with it only. This approach allows AnaLog to
   * watch arbitrary plain logs independently of its configuration. Particularly, it means that a user can set any
   * path into AnaLog's address line and start to watch it the same way as if it was pre-configured as a choice
   * variant in AnaLog configuration.
   *
   * @param path full path of log file to watch for
   * @return newly created log config entry for the specified path
   */
  @NotNull
  private LogConfigEntry createPlainLogConfigEntry(String path) {
    LogConfigEntry artificialEntry = new LogConfigEntry();
    artificialEntry.setPath(path);
    artificialEntry.setNode(clusterProperties.getMyselfNode().getName());
    // Perhaps it's worth here to parse the path and extract node name if it is specified like
    // '~~angara~~/pub/home/analog/out.log'. This would be however applicable to paths specified in URL only
    // because paths configured in file may conflict with each other's node specification.
    artificialEntry.setTitle(extractFileName(path));
    log.debug("New plain log config entry created for path '{}'", path);
    return artificialEntry;
  }

  @NotNull
  private LogConfigEntry findCompositeLogConfigEntry(String uid) {
    LogConfigEntry matchingEntry = choiceProperties.getChoices().stream()
        .flatMap(choiceGroup -> choiceGroup.getCompositeLogs().stream())
        .filter(entry -> entry.getUid().equals(uid))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(format("No log configuration entry found for uid=%s", uid)));
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
          // additionally warn the user if this entry contains other includes
          if (!entry.getIncludes().isEmpty()) {
            log.warn("Encountered included log config entry that itself contains other included entries. Nested " +
                "inclusion levels deeper than 2 are not supported and will be ignored. Invalid entry: {}", entry);
          }
        });
  }

  private void stopTrackingOnServer(LogConfigEntry logConfigEntry) {
    switchTrackingOnServer(logConfigEntry, false, false);
  }

  private void startTrackingOnServer(LogConfigEntry logConfigEntry, boolean isTailNeeded) {
    switchTrackingOnServer(logConfigEntry, true, isTailNeeded);
  }

  private void switchTrackingOnServer(LogConfigEntry logConfigEntry, boolean isOn, boolean isTailNeeded) {
    assert !(isTailNeeded && !isOn) : "isTailNeeded flag should not be raised when switching the tracking off";
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    String fullPath = buildFullPath(logConfigEntry);
    String nodeName = nvls(logConfigEntry.getNode(), myselfNode.getName());

    // send registration request for the main entry
    TrackingRequest primaryRequest = new TrackingRequest(fullPath, logConfigEntry.getTimestamp(), nodeName, logConfigEntry.getUid(), isTailNeeded);
    log.debug("Switching {} the registration by PRIMARY request: {}", isOn ? "ON":"OFF", primaryRequest);
    remoteGateway.switchRegistration(primaryRequest, isOn);

    // send registration requests for included entries
    for (LogConfigEntry included : logConfigEntry.getIncludes()) {
      TrackingRequest includedRequest = null;
      try {
        includedRequest = new TrackingRequest(
            convertPathToUnix(included.getPath()),
            included.getTimestamp(),
            nvls(included.getNode(), myselfNode.getName()),
            logConfigEntry.getUid(),
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

  private String buildFullPath(LogConfigEntry logConfigEntry) {
    // The absence of timestamp indirectly points that this entry is "artificial" one and thus doesn't belong to
    // any group. Hence there is no need to build full path for it. It is not the most reliable way to do it though.
    if (logConfigEntry.isPlain()) {
      return logConfigEntry.getPath();
    }

    String groupPathBase = choiceProperties.getChoices().stream()
        .filter(group -> group.getPathBase() != null)
        .filter(group -> group.getCompositeLogs().contains(logConfigEntry))
        .findAny()
        .map(ChoiceGroup::getPathBase)
        .orElse("");
    return convertPathToUnix((groupPathBase + logConfigEntry.getPath()));
  }

}
