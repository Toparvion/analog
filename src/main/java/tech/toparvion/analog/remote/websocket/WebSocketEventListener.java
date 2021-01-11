package tech.toparvion.analog.remote.websocket;

import com.google.common.base.Throwables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import tech.toparvion.analog.model.ServerFailure;
import tech.toparvion.analog.model.config.ChoiceProperties;
import tech.toparvion.analog.model.config.entry.*;
import tech.toparvion.analog.model.remote.TrackingRequest;
import tech.toparvion.analog.remote.server.RegistrationChannelCreator;
import tech.toparvion.analog.remote.server.RemoteGateway;
import tech.toparvion.analog.util.LocalizedLogger;
import tech.toparvion.analog.util.PathUtils;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.util.AnaLogUtils.doSafely;
import static tech.toparvion.analog.util.PathUtils.extractFileName;

/**
 * A component responsible for supporting lifecycle of websocket sessions and watching subscriptions.
 * Built as listener of websocket-specific application events.
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class WebSocketEventListener {
  private final ChoiceProperties choiceProperties;
  private final WatchRegistry registry;
  private final RegistrationChannelCreator registrationChannelCreator;
  private final RemoteGateway remoteGateway;
  private final SimpMessagingTemplate messagingTemplate;
  private final Converter<String, LogPath> converter;

  private final LocalizedLogger log;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")    // IDEA doesn't see remoteGateway
  public WebSocketEventListener(ChoiceProperties choiceProperties,
                                WatchRegistry registry,
                                RegistrationChannelCreator registrationChannelCreator,
                                RemoteGateway remoteGateway,
                                SimpMessagingTemplate messagingTemplate,
                                Converter<String, LogPath> converter,
                                MessageSource messageSource) {
    this.choiceProperties = choiceProperties;
    this.registry = registry;
    this.registrationChannelCreator = registrationChannelCreator;
    this.remoteGateway = remoteGateway;
    this.messagingTemplate = messagingTemplate;
    this.converter = converter;

    log = new LocalizedLogger(this, messageSource);
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
    boolean isTailNeeded = isTailNeeded(headers);
    String sessionId = headers.getSessionId();
    String destination = headers.getDestination();
    Assert.isTrue((destination != null) && destination.startsWith(WEBSOCKET_TOPIC_PREFIX),
            "Subscriber's 'destination' header is absent or malformed: " + destination);
    // Remove '/topic/' prefix as it plays a role for websocket communication only and has no effect for the watching
    String path = destination.replace(WEBSOCKET_TOPIC_PREFIX, "");
    // It's important to distinguish plain logs from composite ones because they are different in the config logic
    boolean isComposite = LogType.COMPOSITE.matches(path);
    log.debug("Received start watching (SUBSCRIBE) command for {} log '{}' within session id={}.",
        (isComposite ? "composite" : "plain"), path, sessionId);

    // Then find or create corresponding log config entry
    AbstractLogConfigEntry logConfig = isComposite
        ? findCompositeLogConfigEntry(path)
        : createPlainLogConfigEntry(path);

    // Find out whether this log is already being watched by any client(s)
    List<String> watchingSessionIds = registry.findWatchingSessionsFor(logConfig);
    if (watchingSessionIds == null) {
      try {
        log.debug("No watching existed for {} log '{}' before. Starting new one...",
            (isComposite ? "composite" : "plain"), logConfig.getId());
        // 1. Ensure that all RMI registration channels are created
        ensureRegistrationChannelsCreated(logConfig);
        // 2. Register the tracking on specified nodes
        startTracking(logConfig, path, isTailNeeded);
        // 3. Remember the tracking in the registry
        registry.addEntry(logConfig, sessionId);
        log.info("New tracking for log '{}' has started within session id={}.", logConfig.getId(), sessionId);

      } catch (Exception e) {
        log.error(format("Failed to start watching of log '%s'.", logConfig.getId()), e);
        Throwable rootCause = Throwables.getRootCause(e);
        ServerFailure failure = new ServerFailure(rootCause.getMessage(), now());
        messagingTemplate.convertAndSend(destination, failure, singletonMap(MESSAGE_TYPE_HEADER, MessageType.FAILURE));
      }

    } else {    // i.e. if there are watching sessions already in registry
      Assert.state(!watchingSessionIds.contains(sessionId),
          format("Session id=%s is already watching log '%s'. Double subscription is prohibited.", sessionId,
              logConfig.getId()));
      watchingSessionIds.add(sessionId);
      log.info("There were {} session(s) already watching log '{}'. New session {} has been added to them.",
          watchingSessionIds.size()-1, logConfig.getId(), sessionId);
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

  /**
   * @param message a message received from client upon unsubscription or disconnecting
   * @param isUnsubscribing {@code true} if client is stopping current subscription and {@code false} if client is
   *                                    disconnecting from the server
   */
  private void stopTrackingIfNeeded(Message<byte[]> message, boolean isUnsubscribing) {
    StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
    String sessionId = headers.getSessionId();
    log.debug("Unsubscribing session with id={}...", sessionId);
    // find all sessions watching the same log (including current one)
    List<String> fellows = registry.findFellowsFor(sessionId);
    // check if there is no such session(s)
    if (fellows == null) {
      if (isUnsubscribing) {      // in case of unsubscribing it is incorrect situation
        log.warn("No fellow session(s) found for sessionId={}", sessionId);
      } else {                    // but in case of disconnecting it is quite right
        log.info("No fellow session found for sessionId={}.", sessionId);
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
    // in case it was the latest session watching that log we should unsubscribe current node from the agent
    AbstractLogConfigEntry watchingLog = registry.findLogConfigEntryBy(sessionId);
    log.debug("No sessions left watching log '{}'. Will unsubscribe current node...", watchingLog.getId());
    doSafely(getClass(), () -> stopTracking(watchingLog));
    // now that the log is not watched anymore on current node we need to remove it from the registry
    registry.removeEntry(watchingLog);
    log.info("Current node has unregistered itself from tracking log '{}' as there is no watching sessions anymore.",
            watchingLog.getId());
  }

  private boolean isTailNeeded(StompHeaderAccessor headers) {
    final String IS_TAIL_NEEDED_HEADER = "isTailNeeded";
    List<String> rawHeaderValue = headers.getNativeHeader(IS_TAIL_NEEDED_HEADER);
    Assert.isTrue((rawHeaderValue != null) && (rawHeaderValue.size() == 1),
        format("'%s' header of SUBSCRIBE command is absent or malformed", IS_TAIL_NEEDED_HEADER));
    return Boolean.parseBoolean(rawHeaderValue.get(0));
  }

  /**
   * When web client sends SUBSCRIBE command for a plain log, AnaLog doesn't search for corresponding log config
   * entry. Instead it just creates new ('artificial') entry and then works with it only. This approach allows AnaLog
   * to watch arbitrary plain logs independently of its configuration. Particularly, it means that a user can set any
   * path into AnaLog's address line and start to watch it the same way as if it was pre-configured as a choice
   * variant in configuration file.
   * <p>This logic doesn't apply to composite logs (yet), but it would be great to implement it someday.
   *
   * @param path full path of log file to watch for
   * @return newly created log config entry for the specified path
   */
  @NotNull
  private AbstractLogConfigEntry createPlainLogConfigEntry(String path) {
    LogPath logPath = converter.convert(path);
    Assert.notNull(logPath,"Path '%s' was converted into 'null' LogPath");
    PlainLogConfigEntry entry = new PlainLogConfigEntry();
    entry.setPath(logPath);
    entry.setTitle(extractFileName(logPath.getFullPath()));
    entry.setSelected(false);
    log.debug("For path '{}' new plain log config entry created: {}", path, entry);
    return entry;
  }

  @NotNull
  private AbstractLogConfigEntry findCompositeLogConfigEntry(String destination) {
    Assert.isTrue(LogType.COMPOSITE.matches(destination),
        "destination for a composite log must start with 'composite://' prefix");
    String typePrefix = LogType.COMPOSITE.getPrefix() + PathUtils.CUSTOM_SCHEMA_SEPARATOR;
    String unprefixedDestination = destination.replace(typePrefix, "");
    List<CompositeLogConfigEntry> matchingEntries = choiceProperties.getChoices().stream()
            .flatMap(choiceGroup -> choiceGroup.getCompositeLogs().stream())
            .filter(entry -> entry.matches(unprefixedDestination))
            .collect(toList());
    if (matchingEntries.isEmpty()) {
      throw new IllegalArgumentException(format("No log configuration entry found for destination=%s", destination));
    }
    if (matchingEntries.size() > 1) {
      log.warn("Multiple matching entries found for destination={}. Will pick the first one.\n{}", destination, matchingEntries);
    }
    CompositeLogConfigEntry matchingEntry = matchingEntries.get(0);
    log.debug("Found matching composite log config entry: {}", matchingEntry);
    return matchingEntry;
  }

  private void ensureRegistrationChannelsCreated(AbstractLogConfigEntry matchingEntry) {
    if (matchingEntry.getType() == LogType.COMPOSITE) {
      CompositeLogConfigEntry compositeEntry = (CompositeLogConfigEntry) matchingEntry;
      compositeEntry.getIncludes().stream()
          .map(CompositeInclusion::getPath)
          .map(LogPath::getNode)
          .forEach(registrationChannelCreator::createRegistrationChannelIfNeeded);

    } else {
      PlainLogConfigEntry plainEntry = (PlainLogConfigEntry) matchingEntry;
      String entryNodeName = plainEntry.getPath().getNode();
      registrationChannelCreator.createRegistrationChannelIfNeeded(entryNodeName);
    }
  }

  private void startTracking(AbstractLogConfigEntry logConfigEntry, String destination, boolean isTailNeeded) {
    switchTracking(logConfigEntry, destination, true, isTailNeeded);
  }

  private void stopTracking(AbstractLogConfigEntry logConfigEntry) {
    switchTracking(logConfigEntry, null, false, false);
  }

  private void switchTracking(AbstractLogConfigEntry logConfigEntry,
                              @Nullable String destination /*can be null when switching OFF*/,
                              boolean isOn,
                              boolean isTailNeeded) {
    Assert.isTrue(!(isTailNeeded && !isOn), "isTailNeeded flag shouldn't be raised when switching tracking off");

    // handling plain entries is quite simple so let's do it first
    if (logConfigEntry instanceof PlainLogConfigEntry) {
      PlainLogConfigEntry plainEntry = (PlainLogConfigEntry) logConfigEntry;
      TrackingRequest request = new TrackingRequest(plainEntry.getPath(), null, destination, isTailNeeded);
      log.debug("sending-plain-tracking-request", (isOn ? "ON" : "OFF"), request);
      remoteGateway.switchRegistration(request, isOn);
      return;
    }

    // handling composite entries involves iteration over all included paths
    CompositeLogConfigEntry compositeEntry = (CompositeLogConfigEntry) logConfigEntry;
    for (CompositeInclusion inclusion : compositeEntry.getIncludes()) {
      TrackingRequest request = new TrackingRequest(
          inclusion.getPath(),
          inclusion.getTimestamp(),
          destination,
          isTailNeeded);
      log.debug("sending-composite-tracking-request", isOn ? "ON" : "OFF", request);
      remoteGateway.switchRegistration(request, isOn);
      // This action may end up with an exception and thus interrupt the whole loop. While it is generally a bad
      // practice, here it is considered OK as it allows to react on tracking faults in a fail-fast fashion.
    }
  }

}
