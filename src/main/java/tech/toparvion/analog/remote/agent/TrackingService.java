package tech.toparvion.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowRegistration;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.TrackingRequest;
import tech.toparvion.analog.remote.agent.misc.AddressAwareRmiOutboundGateway;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.remote.agent.TailingFlowProvider.AGGREGATOR_OUTPUT_CHANNEL_NAME;
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
  private static final Logger log = LoggerFactory.getLogger(TrackingService.class);
  private static final String PAYLOAD_OUT_GATEWAY_BEAN_NAME_PREFIX = "payloadOutGateway:";
  private static final String COMPOSITE_WATCH_FLOW_PREFIX = "compositeWatch:";
  private static final String PLAIN_WATCH_FLOW_PREFIX = "plainWatch:";

  /**
   * The registry of watchers' gateways: log path -> set of payload outbound gateway flow ids
   * TODO удалить этот реестр, так как мы должны полагаться только на данные из FlowContext
   */
  private final Map<String, Set<String>> sendingRegistry = new HashMap<>();

  private final IntegrationFlowContext flowContext;
  private final TimestampExtractor timestampExtractor;
  private final TailingFlowProvider trackingFlowProvider;


  @Autowired
  public TrackingService(IntegrationFlowContext flowContext,
                         TimestampExtractor timestampExtractor,
                         TailingFlowProvider trackingFlowProvider) {
    this.flowContext = flowContext;
    this.timestampExtractor = timestampExtractor;
    this.trackingFlowProvider = trackingFlowProvider;
  }

  /**
   * Initiates tracking process for the log specified in {@code request}: <ol>
   *   <li>Finds or {@link TailingFlowProvider#provideAggregatingFlow(String, boolean) creates} tailing flow (alongside
   *   with the {@link TimestampExtractor#registerNewTimestampFormat(String, String) registration}
   *   of the specified timestamp format);</li>
   *   <li>Creates new {@code RmiOutboundGateway} capable of sending messages to the {@code watcherAddress} and makes
   *   it a subscriber for the tailing flow output channel;</li>
   *   <li>Stores the gateway in a {@link TrackingService#sendingRegistry registry} in order to find it when turning
   *   the tracking off.</li>
   * </ol>
   * @param request holder of parameters for tracking creation
   * @param watcherAddress address of the node to which the tracked messages should be sent
   */
  void registerWatcher(TrackingRequest request, InetSocketAddress watcherAddress) {
    log.info("Получен запрос на регистрацию наблюдателя {} за логом {}.", watcherAddress, request);
    String logPath = request.getLogFullPath();
    boolean isPlainLogRequest = request.isPlain();
    StandardIntegrationFlow trackingFlow;

    String trackingFlowId = isPlainLogRequest
        ? PLAIN_WATCH_FLOW_PREFIX + logPath
        : COMPOSITE_WATCH_FLOW_PREFIX + logPath;
    IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);
    if (trackingRegistration != null) {
      trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
      //log.info("Found existing log tracking flow with id={}.", trackingRegistrationId);
      log.info("Найдено существующее слежение с id={}.", trackingRegistration);

    } else {
      if (!isPlainLogRequest) {
        log.debug("Создаю новое агрегирующее слежение для лога '{}'...", logPath);
        trackingRegistration = flowContext
            .registration(trackingFlowProvider.provideAggregatingFlow(logPath, request.isTailNeeded()))
            .autoStartup(true)
            .id(COMPOSITE_WATCH_FLOW_PREFIX +logPath)
            .useFlowIdAsPrefix()
            .register();
        trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
        timestampExtractor.registerNewTimestampFormat(request.getTimestampFormat(), logPath);
        //log.info("Created new AGGREGATING log tracking flow with id={}.", registration.getId());
        log.info("Создано новое АГРЕГИРУЮЩЕЕ слежение для лога '{}' с id={}.", logPath, trackingRegistration.getId());

      } else {
        log.debug("Создаю новое простое слежение для лога '{}'...", logPath);
        trackingRegistration = flowContext
            .registration(trackingFlowProvider.providePlainFlow(logPath, request.isTailNeeded()))
            .autoStartup(true)
            .id(PLAIN_WATCH_FLOW_PREFIX + logPath)
            .useFlowIdAsPrefix()
            .register();
        trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
        //log.info("Created new PLAIN log tracking flow with id={}.", registration.getId());
        log.info("Создано новое ПРОСТОЕ слежение для лога '{}' с id={}.", logPath, trackingRegistration.getId());
      }
    }

    // now that tracking is up, let's establish outbound sending channel
    PublishSubscribeChannel outChannel = extractOutChannel(trackingFlow);
    String payloadSendingUrl = format("rmi://%s:%d/%s%s",
        watcherAddress.getHostName(),
        watcherAddress.getPort(),
        RmiInboundGateway.SERVICE_NAME_PREFIX,
        SERVER_RMI_PAYLOAD_IN__CHANNEL);
    AddressAwareRmiOutboundGateway payloadOutGateway = new AddressAwareRmiOutboundGateway(watcherAddress, payloadSendingUrl);

    IntegrationFlowRegistration sendingRegistration = flowContext.registration(
        IntegrationFlows
            .from(outChannel)
            .enrichHeaders(e -> e.header(LOG_CONFIG_ENTRY_UID__HEADER, request.getUid()))
            .enrichHeaders(e -> e.header(SOURCE_NODE__HEADER, request.getNodeName()))
            .handle(payloadOutGateway, spec -> spec.id(PAYLOAD_OUT_GATEWAY_BEAN_NAME_PREFIX+logPath))
            .get())
//        .id(plainOrCompositPrefix + watcherAddress)
        // TODO тогда можно будет найти этот sending flow по такому ID и удалить его без sendingRegistry :-)
        .autoStartup(true)
        .register();

    sendingRegistry.computeIfAbsent(logPath, s -> new HashSet<>())
                   .add(sendingRegistration.getId());
    log.debug("Для лога {} зарегистрирован новый слушатель: {} (регистрация: '{}').", logPath,
        payloadOutGateway.getGatewayAddress(), sendingRegistration.getId());
    //log.debug("Registered new payloadOutGateway with address {}", payloadOutGateway.getGatewayAddress());
  }

  /**
   * Unsubscribes the requested watcher from corresponding tailing flow and removes the latter if there's no more
   * watchers for it anymore.
   * @param watcherAddress address of watcher to unsubscribe
   * @param request parameters of log being tracked
   */
  void unregisterWatcher(TrackingRequest request, InetSocketAddress watcherAddress) {
    String logPath = request.getLogFullPath();
    log.info("Получен запрос на дерегистрацию наблюдателя {} для лога: '{}'", watcherAddress, logPath);
    boolean isPlainLogRequest = request.isPlain();

    // находим соответствующее слежение и извлекаем из него выходной канал
    String trackingFlowId = isPlainLogRequest
        ? PLAIN_WATCH_FLOW_PREFIX + logPath
        : COMPOSITE_WATCH_FLOW_PREFIX + logPath;
    IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);
    if (trackingRegistration == null) {
      log.warn("Нельзя удалить слежение по id='{}', так как оно не зарегистрировано.", trackingFlowId);
      return;
    }
    StandardIntegrationFlow trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
    PublishSubscribeChannel outChannel = extractOutChannel(trackingFlow);

    // находим соответствующего получателя
    Set<String> watchersFlowIds = sendingRegistry.get(logPath);
    assert (watchersFlowIds != null)
        : String.format("No watchersFlowIds found in sending registry for logPath '%s'", logPath);
    String sendingFlowId = watchersFlowIds.stream()
        .filter(flowId -> findGateway(flowId, logPath).getGatewayAddress().equals(watcherAddress))
        .findAny()
        .orElseThrow(IllegalStateException::new);

    // безопасно отписываем наблюдателя от канала
    doSafely(log, () -> flowContext.remove(sendingFlowId));
    watchersFlowIds.remove(sendingFlowId);
    log.debug("Процесс слежения с регистрацией id='{}' отписан от канала '{}'.", sendingFlowId, outChannel);
//    log.debug("Watcher '{}' has been unsubscribed from channel '{}'.", registeredWatcher, outChannel);

    if (watchersFlowIds.isEmpty()) {
      // wrap into safe action to guarantee that sending and tracking registries will be updated accordingly
      doSafely(log, () -> flowContext.remove(trackingFlowId));
      sendingRegistry.remove(logPath);
      log.debug("Для лога '{}' не осталось наблюдателей. Слежение прекращено.", logPath);
//      log.debug("There is no watchers for log {} anymore. Tracking removed.", logPath);
    }
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow) {
    return logTrackingFlow.getIntegrationComponents().keySet()
        .stream()
        .filter(PublishSubscribeChannel.class::isInstance)
        .map(PublishSubscribeChannel.class::cast)
        .filter(channel -> AGGREGATOR_OUTPUT_CHANNEL_NAME.endsWith(channel.getComponentName()))
        .findAny()
        .orElseThrow(() -> new IllegalStateException(format("No '%s' found among components of logTrackingFlow: %s",
            AGGREGATOR_OUTPUT_CHANNEL_NAME, logTrackingFlow.getIntegrationComponents().keySet()
                                                                                      .stream()
                                                                                      .map(Object::toString)
                                                                                      .collect(joining()))));
  }

  private AddressAwareRmiOutboundGateway findGateway(String flowId, String logPath) {
    IntegrationFlowRegistration registration = flowContext.getRegistrationById(flowId);
    assert registration != null;
    String gatewayName = PAYLOAD_OUT_GATEWAY_BEAN_NAME_PREFIX + logPath;
    StandardIntegrationFlow flow = (StandardIntegrationFlow) registration.getIntegrationFlow();

    Object lastComponent = flow.getIntegrationComponents().entrySet()
        .stream()
        .filter(entry -> gatewayName.equals(entry.getValue()))
        .map(Map.Entry::getKey)
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(format("No bean with name '%s' found among components of " +
                "flow with id=%s", gatewayName, flowId)));
    ConfigurablePropertyAccessor propertyAccessor = PropertyAccessorFactory.forDirectFieldAccess(lastComponent);
    return  (AddressAwareRmiOutboundGateway) propertyAccessor.getPropertyValue("handler");
  }

  @EventListener
  public void processFileTailingEvent(FileTailingEvent tailingEvent) {
    log.debug("Received file tailing event: {}", tailingEvent.toString());
    String logPath = convertPathToUnix(tailingEvent.getFile().getAbsolutePath());
    Set<String> watchersFlowIds = sendingRegistry.get(logPath);
    if (watchersFlowIds == null) {
      // sometimes tailing event may arrive earlier than the whole watching flow is built, e.g. when the file to
      // watch is absent and tail utility detects it immediately (see "Stream closed" message in logs)
      log.warn("No watching flow ID found in registry by logPath='{}'.", logPath);
      return;
    }
    for (String flowId : watchersFlowIds) {
      IntegrationFlowRegistration registration = flowContext.getRegistrationById(flowId);
      assert (registration != null);
      MessagingTemplate messagingTemplate = registration.getMessagingTemplate();
      messagingTemplate.send(MessageBuilder.withPayload(tailingEvent).build());
      log.trace("Sent tailing event of file '{}' to the flow id='{}'", tailingEvent.getFile().getAbsolutePath(), flowId);
    }
  }

  @ManagedAttribute
  public String[] getSendingRegistryKeys() {
    return sendingRegistry.keySet().toArray(new String[0]);
  }

}
