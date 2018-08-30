package tech.toparvion.analog.remote.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.integration.rmi.RmiOutboundGateway;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.TrackingRequest;
import tech.toparvion.analog.util.LocalizedLogger;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.LinkedList;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static org.springframework.integration.dsl.context.IntegrationFlowContext.IntegrationFlowRegistration;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.util.AnaLogUtils.convertPathToUnix;
import static tech.toparvion.analog.util.AnaLogUtils.doSafely;

/**
 * Applied logical service providing routines for remote log tracking.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
@ManagedResource
public class TrackingService {
  private static final String GROUPING_PREFIX = "grouping_";
  private static final String FLAT_PREFIX = "flat_";

  private final IntegrationFlowContext flowContext;
  private final TimestampExtractor timestampExtractor;
  private final TailingFlowProvider trackingFlowProvider;

  private final LocalizedLogger log;

  @Autowired
  public TrackingService(IntegrationFlowContext flowContext,
                         TimestampExtractor timestampExtractor,
                         TailingFlowProvider trackingFlowProvider,
                         MessageSource messageSource) {
    this.flowContext = flowContext;
    this.timestampExtractor = timestampExtractor;
    this.trackingFlowProvider = trackingFlowProvider;

    log = new LocalizedLogger(this, messageSource);
  }

  /**
   * Initiates tracking process for the log specified in {@code request}: <ol>
   *   <li>Finds or {@link TailingFlowProvider#provideGroupingFlow(String, boolean) creates} tailing flow (alongside
   *   with the {@link TimestampExtractor#registerNewTimestampFormat(String, String) registration}
   *   of the specified timestamp format);</li>
   *   <li>Creates new {@code RmiOutboundGateway} capable of sending messages to the {@code watcherAddress} and makes
   *   it a subscriber for the tailing flow output channel.</li>
   * </ol>
   * @param request holder of parameters for tracking creation
   * @param watcherAddress address of the node to which the tracked messages should be sent
   */
  void registerWatcher(TrackingRequest request, InetSocketAddress watcherAddress) {
    log.info("received-reg-watcher-request", watcherAddress, request);
    StandardIntegrationFlow trackingFlow = findExistingTrackingFlow(request);
    if (trackingFlow == null) {
      if (request.isFlat()) {
        trackingFlow = startFlatTrackingFlow(request);

      } else {
        trackingFlow = startGroupingTrackingFlow(request);
      }
    }
    subscribeWatcherToTrackingFlow(trackingFlow, watcherAddress, request);
  }

  @Nullable
  private StandardIntegrationFlow findExistingTrackingFlow(TrackingRequest request) {
    String logPath = request.getLogFullPath();
    String flowPrefix = request.isFlat() ? FLAT_PREFIX : GROUPING_PREFIX;
    String trackingFlowId = composeTrackingFlowId(flowPrefix, logPath);
    IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);

    if (trackingRegistration == null) {
      log.debug("not-found-existing-tracking-flow", trackingFlowId);
      return null;
    }
    log.info("found-existing-tracking-flow", trackingFlowId);
    return (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
  }

  private StandardIntegrationFlow startFlatTrackingFlow(TrackingRequest request) {
    String logPath = request.getLogFullPath();
    log.debug("creating-new-flat-flow", logPath);
    IntegrationFlow flatFlow = trackingFlowProvider
        .provideFlatFlow(logPath, request.isTailNeeded());
    IntegrationFlowRegistration trackingRegistration = flowContext
        .registration(flatFlow)
        .autoStartup(true)
        .id(composeTrackingFlowId(FLAT_PREFIX, logPath))
        .useFlowIdAsPrefix()
        .register();
    log.info("created-new-flat-flow", logPath, trackingRegistration.getId());
    return (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
  }

  private StandardIntegrationFlow startGroupingTrackingFlow(TrackingRequest request) {
    String logPath = request.getLogFullPath();
    log.debug("creating-new-grouping-flow", logPath);
    IntegrationFlow groupingFlow = trackingFlowProvider
        .provideGroupingFlow(logPath, request.isTailNeeded());
    IntegrationFlowRegistration trackingRegistration = flowContext
        .registration(groupingFlow)
        .autoStartup(true)
        .id(composeTrackingFlowId(GROUPING_PREFIX, logPath))
        .useFlowIdAsPrefix()
        .register();
    timestampExtractor.registerNewTimestampFormat(request.getTimestampFormat(), logPath);
    log.info("created-new-grouping-flow", logPath, trackingRegistration.getId());
    return (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
  }

  private void subscribeWatcherToTrackingFlow(StandardIntegrationFlow trackingFlow,
                                              InetSocketAddress watcherAddress,
                                              TrackingRequest request) {
    String logPath = request.getLogFullPath();
    String sendingFlowId = composeSendingFlowId(request, watcherAddress);
    if (flowContext.getRegistrationById(sendingFlowId) != null) {
      log.warn("found-duplicate-watcher", sendingFlowId);
      return;
    }
    log.debug("creating-new-sending-flow", sendingFlowId);
    // by this moment the tracking must be already set up, so it's time to establish outbound sending channel
    PublishSubscribeChannel outChannel = extractOutChannel(trackingFlow);
    String payloadSendingUrl = format("rmi://%s:%d/%s%s",
        watcherAddress.getHostName(),
        watcherAddress.getPort(),
        RmiInboundGateway.SERVICE_NAME_PREFIX,
        SERVER_RMI_PAYLOAD_IN__CHANNEL);

    StandardIntegrationFlow sendingFlow = IntegrationFlows
        .from(outChannel)
        .enrichHeaders(e -> e.header(LOG_CONFIG_ENTRY_UID__HEADER, request.getUid()))
        .enrichHeaders(e -> e.header(SOURCE_NODE__HEADER, request.getNodeName()))
        .handle(new RmiOutboundGateway(payloadSendingUrl))
        .get();
    IntegrationFlowRegistration sendingRegistration = flowContext
        .registration(sendingFlow)
        .id(sendingFlowId)
        .useFlowIdAsPrefix()
        .autoStartup(true)
        .register();

    log.debug("subscribed-new-watcher", logPath, sendingRegistration.getId());
  }

  /**
   * Unsubscribes the requested watcher from corresponding tailing flow and removes the latter if there's no more
   * watchers for it anymore.
   * @param watcherAddress address of watcher to unsubscribe
   * @param request parameters of log being tracked
   */
  void unregisterWatcher(TrackingRequest request, InetSocketAddress watcherAddress) {
    String logPath = request.getLogFullPath();
    log.info("received-unreg-watcher-request", watcherAddress, logPath);

    // находим соответствующее слежение и извлекаем из него выходной канал
    String flowPrefix = request.isFlat() ? FLAT_PREFIX : GROUPING_PREFIX;
    String trackingFlowId = composeTrackingFlowId(flowPrefix, logPath);
    IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);
    if (trackingRegistration == null) {
      log.warn("not-found-tracking-registration", trackingFlowId);
      return;
    }
    StandardIntegrationFlow trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
    PublishSubscribeChannel outChannel = extractOutChannel(trackingFlow);

    // находим соответствующего получателя
    String sendingFlowId = composeSendingFlowId(request, watcherAddress);
    IntegrationFlowRegistration sendingRegistration = flowContext.getRegistrationById(sendingFlowId);
    if (sendingRegistration == null) {
      log.warn("not-found-sending-registration", sendingFlowId);
      return;
    }
    // безопасно отписываем наблюдателя от канала
    doSafely(getClass(), () -> flowContext.remove(sendingFlowId));   // this also unsubscribes the flow from outChannel
    log.debug("unsubscribed-watcher", sendingFlowId, outChannel);
//    log.debug("Watcher '{}' has been unsubscribed from channel '{}'.", registeredWatcher, outChannel);

    if (outChannel.getSubscriberCount() < 1) {
      // wrap into safe action to prevent error propagation in case of removal failure
      doSafely(getClass(), () -> flowContext.remove(trackingFlowId));
      log.debug("stopped-tracking", logPath);
    }
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow) {
    LinkedList<PublishSubscribeChannel> channels = logTrackingFlow.getIntegrationComponents()
        .keySet()
        .stream()
        .filter(PublishSubscribeChannel.class::isInstance)
        .map(PublishSubscribeChannel.class::cast)
        .collect(toCollection(LinkedList::new));
    if (channels.isEmpty()) {
      throw new IllegalStateException(format("No PublishSubscribeChannel found among components of logTrackingFlow: %s",
          logTrackingFlow.getIntegrationComponents().keySet()
              .stream()
              .map(Object::toString)
              .collect(joining())));
    }
    return channels.getLast();
  }

  @EventListener
  public void processFileTailingEvent(FileTailingEvent tailingEvent) {
    log.debug("received-tailing-event", tailingEvent.toString());
    String logPath = convertPathToUnix(tailingEvent.getFile().getAbsolutePath());

    for (String prefix : new String[]{FLAT_PREFIX, GROUPING_PREFIX}) {
      String trackingFlowId = composeTrackingFlowId(prefix, logPath);
      log.trace("trying-to-find-tracking-flow", trackingFlowId);
      IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);
      if (trackingRegistration == null) {
        log.trace("not-found-tracking-registration-by-id", trackingFlowId);
        continue;
      }
      StandardIntegrationFlow trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
      PublishSubscribeChannel trackingOutChannel = extractOutChannel(trackingFlow);
      trackingOutChannel.send(MessageBuilder.withPayload(tailingEvent).build());
      log.debug("sent-tailing-event", tailingEvent, trackingOutChannel.getComponentName());
    }
  }

  private static String composeTrackingFlowId(String flowPrefix, String logPath) {
    return flowPrefix + logPath;
  }

  private static String composeSendingFlowId(TrackingRequest request, InetSocketAddress watcherAddress) {
    String logPath = request.getLogFullPath();
    String flowsPrefix = request.isFlat() ? FLAT_PREFIX : GROUPING_PREFIX;
    return flowsPrefix + watcherAddress + "_" + logPath;
  }

}
