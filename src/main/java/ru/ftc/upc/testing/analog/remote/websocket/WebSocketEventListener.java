package ru.ftc.upc.testing.analog.remote.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import ru.ftc.upc.testing.analog.model.TrackingRequest;
import ru.ftc.upc.testing.analog.model.config.*;
import ru.ftc.upc.testing.analog.remote.server.RegistrationChannelCreator;
import ru.ftc.upc.testing.analog.remote.server.RemoteGateway;
import ru.ftc.upc.testing.analog.util.Util;

import javax.validation.constraints.NotNull;
import java.util.List;

import static java.lang.String.format;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.WEBSOCKET_TOPIC_PREFIX;
import static ru.ftc.upc.testing.analog.util.Util.nvls;

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

  @Autowired
  public WebSocketEventListener(ChoiceProperties choiceProperties,
                                WatchRegistry registry,
                                RegistrationChannelCreator registrationChannelCreator,
                                ClusterProperties clusterProperties,
                                RemoteGateway remoteGateway) {
    this.choiceProperties = choiceProperties;
    this.registry = registry;
    this.registrationChannelCreator = registrationChannelCreator;
    this.clusterProperties = clusterProperties;
    this.remoteGateway = remoteGateway;
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
    List<String> rawHeaderValue = headers.getNativeHeader("isPlain");
    assert (rawHeaderValue != null) && (rawHeaderValue.size() == 1)
        : "'isPlain' header of SUBSCRIBE command is absent or malformed";
    Boolean isPlain = Boolean.valueOf(rawHeaderValue.get(0));
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
      log.debug("No watching existed for {} log '{}' before. Starting new one...", (isPlain ? "plain" : "composite"),
          logConfig.getUid());
      // 1. Ensure that all RMI registration channels are created
      ensureRegistrationChannelsCreated(logConfig);
      // 2. Register the tracking on specified nodes
      initiateTracking(logConfig);
      // 3. Remember the tracking in the registry
      registry.addEntry(logConfig, sessionId);
      log.info("New tracking for log '{}' has started within session id={}.", logConfig, sessionId);

    } else {    // i.e. if there are watching sessions already in registry
      assert !watchingSessionIds.contains(sessionId)
          : String.format("Session id=%s is already watching log '%s'. Double subscription is prohibited.",
          sessionId, logConfig);
      watchingSessionIds.add(sessionId);
      log.info("There were {} sessions already watching log '{}'. New session {} has been added to them.",
          watchingSessionIds.size()-1, logConfig, sessionId);
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
        throw new IllegalStateException("No registered session(s) found for sessionId=" + sessionId);
      } else {                    // but in case of disconnecting it is quite right
        log.info("No registered session found for sessionId={}.", sessionId);
        return;
      }
    }
    // check if there any other session(s) left (may require synchronization on registry object)
    if (fellows.size() > 1) {
      fellows.remove(sessionId);
      log.info("There are still {} session(s) watching the same log as removed one (id={}). " +
          "Will keep the tracking active.", fellows.size(), sessionId);
      return;
    }
    // in case it was the latest session watching that log we should stop the tracking
    LogConfigEntry watchingLog = registry.findLogConfigEntryBy(sessionId);
    log.debug("No sessions left watching log '{}'. Will deactivate the tracking...", watchingLog.getUid());
    String fullPath = buildFullPath(watchingLog);
    String nodeName = nvls(watchingLog.getNode(), clusterProperties.getMyselfNode().getName());
    TrackingRequest request = new TrackingRequest(fullPath, watchingLog.getTimestamp(), nodeName, watchingLog.getUid());
    remoteGateway.switchRegistration(request, false);
    // now that the log is not tracked anymore we need to remove it from the registry
    registry.removeEntry(watchingLog);
    log.info("Current node has unregistered itself from tracking log '{}' as there is no watching sessions anymore.",
        watchingLog.getUid());
  }

  @NotNull
  private LogConfigEntry createPlainLogConfigEntry(String path) {
    LogConfigEntry artificialEntry = new LogConfigEntry();
    artificialEntry.setPath(path);
    // Perhaps it's worth here to parse the path and extract node name if it is specified like
    // '~~angara~~/pub/home/analog/out.log'. This would be however applicable to paths specified in URL only
    // because paths configured in file may conflict with each other's node specification.
    artificialEntry.setTitle(Util.extractFileName(path));
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

  private void initiateTracking(LogConfigEntry matchingEntry) {
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    String fullPath = buildFullPath(matchingEntry);
    String nodeName = nvls(matchingEntry.getNode(), myselfNode.getName());
    // send registration request for the main entry
    TrackingRequest request = new TrackingRequest(fullPath, matchingEntry.getTimestamp(), nodeName, matchingEntry.getUid());
    remoteGateway.switchRegistration(request, true);
    // send registration requests for included entries
    for (LogConfigEntry included : matchingEntry.getIncludes()) {
      request = new TrackingRequest(included.getPath(),
          included.getTimestamp(),
          nvls(included.getNode(), myselfNode.getName()),
          matchingEntry.getUid());
      remoteGateway.switchRegistration(request, true);
      // TODO provide isolation for included logs registration in order to avoid any of them capable of failing others
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
    return groupPathBase + logConfigEntry.getPath();
  }

}
