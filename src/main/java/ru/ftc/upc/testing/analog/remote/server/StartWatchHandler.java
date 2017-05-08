package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.config.ChoiceProperties;
import ru.ftc.upc.testing.analog.model.config.ClusterNode;
import ru.ftc.upc.testing.analog.model.config.ClusterProperties;
import ru.ftc.upc.testing.analog.model.config.LogConfigEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;
import static ru.ftc.upc.testing.analog.util.Util.nvls;

/**
 * @author Toparvion
 * @since v0.7
 */
@Component
public class StartWatchHandler extends AbstractWatchHandler {
  private static final Logger log = LoggerFactory.getLogger(StartWatchHandler.class);

  private final RegistrationChannelCreator registrationChannelCreator;
  private final ClusterProperties clusterProperties;
  private final RemoteGateway remoteGateway;

  @Autowired
  public StartWatchHandler(ChoiceProperties choiceProperties,
                           RegistrationChannelCreator registrationChannelCreator,
                           ClusterProperties clusterProperties,
                           RemoteGateway remoteGateway) {
    super(choiceProperties);
    this.registrationChannelCreator = registrationChannelCreator;
    this.clusterProperties = clusterProperties;
    this.remoteGateway = remoteGateway;
  }

  @Override
  protected SimpMessageType getTargetMessageType() {
    return SUBSCRIBE;
  }

  @Override
  public Message<?> beforeHandleInternal(Message<?> message, MessageChannel channel, SimpleBrokerMessageHandler handler) {
    String uid = getUid(message);
    int clientsCount = clientCounters.computeIfAbsent(uid, s -> new AtomicInteger()).get();
    log.debug("{} client(s) are watching log with uid={} (not including new one).", clientsCount, uid);

    if (clientsCount == 0) {
      log.info("There was no watching clients for uid={} before this moment. Starting watching for this node...", uid);
      // 1. Pick up the log config entry corresponding the one received from the client
      LogConfigEntry logConfig = findMatchingLogConfigEntry(uid);

      // 2. Then ensure that all RMI registration channels are created
      ensureRegistrationChannelsCreated(logConfig);

      // 3. And now register the watching on specified nodes
      initiateWatching(logConfig);
    }

    clientCounters.get(uid).incrementAndGet();    // just now, after doing all the stuff we can change counter value
    return message;
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

  private void initiateWatching(LogConfigEntry matchingEntry) {
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    String fullPath = buildFullPath(matchingEntry);
    String nodeName = nvls(matchingEntry.getNode(), myselfNode.getName());
    remoteGateway.switchRegistration(fullPath, nodeName, matchingEntry.getTimestamp(), true);
    matchingEntry.getIncludes().forEach(included ->
        remoteGateway.switchRegistration(included.getPath(),
            nvls(included.getNode(), myselfNode.getName()),
            included.getTimestamp(),
            true)
    );
  }

}
