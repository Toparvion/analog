package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.TrackingRequest;
import ru.ftc.upc.testing.analog.model.config.ChoiceProperties;
import ru.ftc.upc.testing.analog.model.config.ClusterNode;
import ru.ftc.upc.testing.analog.model.config.ClusterProperties;
import ru.ftc.upc.testing.analog.model.config.LogConfigEntry;

import static org.springframework.messaging.simp.SimpMessageType.UNSUBSCRIBE;
import static ru.ftc.upc.testing.analog.util.Util.nvls;

/**
 * @author Toparvion
 * @since v0.7
 */
@Component
public class StopWatchHandler extends AbstractWatchHandler {
  private static final Logger log = LoggerFactory.getLogger(StopWatchHandler.class);

  private final ClusterProperties clusterProperties;
  private final RemoteGateway remoteGateway;

  @Autowired
  public StopWatchHandler(ChoiceProperties choiceProperties,
                          ClusterProperties clusterProperties,
                          RemoteGateway remoteGateway) {
    super(choiceProperties);
    this.clusterProperties = clusterProperties;
    this.remoteGateway = remoteGateway;
  }

  @Override
  protected SimpMessageType getTargetMessageType() {
    return UNSUBSCRIBE;
  }

  @Override
  protected Message<?> beforeHandleInternal(Message<?> message, MessageChannel channel, SimpleBrokerMessageHandler handler) {
    LogConfigEntry logConfig = findOrCreateLogConfigEntry(message);
    String uid = logConfig.getUid();
    int clientsCount = clientCounters.get(uid).get();
    log.debug("{} client(s) are tracking log with uid={} (including one being processed).", clientsCount, uid);
    if (clientsCount == 1) {
      log.info("There will be no tracking clients for uid={} after processing. Stopping tracking from this node...", uid);
      ClusterNode myselfNode = clusterProperties.getMyselfNode();
      String fullPath = buildFullPath(logConfig);
      String nodeName = nvls(logConfig.getNode(), myselfNode.getName());
      TrackingRequest request = new TrackingRequest(fullPath, logConfig.getTimestamp(), nodeName, logConfig.getUid());
      remoteGateway.switchRegistration(request, false);
    }

    clientCounters.get(uid).decrementAndGet();    // just now, after doing all the stuff we can change counter value
    return message;

    // TODO It is still unaddressed problem how to stop tracking in case a client suddenly disappears 'cause it provokes
    // DISCONNECT only but not UNSUBSCRIBE. 'Hanging' tracking is not a big problem but looks like result of weak design.

  }
}