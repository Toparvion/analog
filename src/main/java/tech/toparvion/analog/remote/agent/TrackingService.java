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
import static tech.toparvion.analog.service.AnaLogUtils.normalizePath;

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

  /**
   * The registry of logs being tracked, where the key is log path being watched and the value is corresponding
   * {@link IntegrationFlowRegistration} id.
   */
  private final Map<String, String> trackingRegistry = new HashMap<>();

  /**
   * The registry of watchers' gateways: log path -> set of payload outbound gateway flow ids
   */
  private final Map<String, Set<String>> sendingRegistry = new HashMap<>();

  private final IntegrationFlowContext flowContext;
  private final TimestampExtractor timestampExtractor;
  private final TailingFlowProvider trackingFlowProvider;


  @Autowired
  public TrackingService(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                         IntegrationFlowContext flowContext,
                         TimestampExtractor timestampExtractor,
                         TailingFlowProvider trackingFlowProvider) {
    this.flowContext = flowContext;
    this.timestampExtractor = timestampExtractor;
    this.trackingFlowProvider = trackingFlowProvider;
  }

  /**
   * Initiates tracking process for the log specified in {@code request}: <ol>
   *   <li>Checks if this is a duplicate registration (the request for the same log came from the same watcher);</li>
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

    // first let's check if it is a duplicate registration request
    Set<String> knownWatcherFlowIds = sendingRegistry.get(logPath);
    if (knownWatcherFlowIds != null && knownWatcherFlowIds.stream()
        .map(flowId -> findGateway(flowId, logPath))
        .map(AddressAwareRmiOutboundGateway::getGatewayAddress)
        .anyMatch(knownAddress -> knownAddress.equals(watcherAddress))) {
      log.warn("Watcher {} is already registered for log '{}'. Skip registration.", watcherAddress, logPath);
      return;
    }

    // the watcher wasn't registered yet; we may go on
    StandardIntegrationFlow trackingFlow;
    String trackingRegistrationId = trackingRegistry.get(logPath);
    if (trackingRegistrationId != null) {
      IntegrationFlowRegistration logTrackingRegistration = flowContext.getRegistrationById(trackingRegistrationId);
      trackingFlow = (StandardIntegrationFlow) logTrackingRegistration.getIntegrationFlow();
      //log.info("Found existing log tracking flow with id={}.", trackingRegistrationId);
      log.info("Найдено существующее слежение с id={}.", trackingRegistrationId);

    } else if (!request.isPlain()) {
      IntegrationFlowRegistration trackingRegistration = flowContext
          .registration(trackingFlowProvider.provideAggregatingFlow(logPath, request.isTailNeeded()))
          .autoStartup(true)
          .register();
      trackingRegistry.put(logPath, trackingRegistration.getId());
      trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
      timestampExtractor.registerNewTimestampFormat(request.getTimestampFormat(), logPath);
      //log.info("Created new AGGREGATING log tracking flow with id={}.", registration.getId());
      log.info("Создано новое АГРЕГИРУЮЩЕЕ слежение для лога '{}' с id={}.", logPath, trackingRegistration.getId());

    } else {
      IntegrationFlowRegistration trackingRegistration = flowContext
          .registration(trackingFlowProvider.providePlainFlow(logPath, request.isTailNeeded()))
          .autoStartup(true)
          .register();
      trackingRegistry.put(logPath, trackingRegistration.getId());
      trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
      //log.info("Created new PLAIN log tracking flow with id={}.", registration.getId());
      log.info("Создано новое ПРОСТОЕ слежение для лога '{}' с id={}.", logPath, trackingRegistration.getId());
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

    // находим соответствующее слежение и извлекаем из него выходной канал
    String trackingLogFlowId = trackingRegistry.get(logPath);
    assert (trackingLogFlowId != null) : "No trackingLogFlowId found for logPath="+logPath;
    IntegrationFlowRegistration trackingLogRegistration = flowContext.getRegistrationById(trackingLogFlowId);
    assert trackingLogRegistration != null;
    StandardIntegrationFlow trackingLogFlow = (StandardIntegrationFlow) trackingLogRegistration.getIntegrationFlow();
    PublishSubscribeChannel outChannel = extractOutChannel(trackingLogFlow);

    // находим соответствующего наблюдателя
    Set<String> watchersFlowIds = sendingRegistry.get(logPath);
    assert (watchersFlowIds != null)
        : String.format("No watchersFlowIds found in sending registry for logPath '%s'", logPath);
    String registeredFlowId = watchersFlowIds.stream()
        .filter(flowId -> findGateway(flowId, logPath).getGatewayAddress().equals(watcherAddress))
        .findAny()
        .orElseThrow(IllegalStateException::new);

    // отписываем наблюдателя от канала
    flowContext.remove(registeredFlowId);
    watchersFlowIds.remove(registeredFlowId);
    log.debug("Процесс слежения с регистрацией id='{}' отписан от канала '{}'.", registeredFlowId, outChannel);
//    log.debug("Watcher '{}' has been unsubscribed from channel '{}'.", registeredWatcher, outChannel);

    if (watchersFlowIds.isEmpty()) {
      flowContext.remove(trackingLogFlowId);
      trackingRegistry.remove(logPath);
      sendingRegistry.remove(logPath);
      log.debug("Для лога '{}' не осталось наблюдателей. Слежение прекращено.", logPath);
//      log.debug("There is no watchers for log {} anymore. Tracking removed.", logPath);
    }
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow) {
    return logTrackingFlow.getIntegrationComponents().keySet()
        .stream()
        .filter(component -> PublishSubscribeChannel.class.isAssignableFrom(component.getClass()))
        .findAny()
        .map(component -> (PublishSubscribeChannel) component)
        .orElseThrow(() -> new IllegalStateException(format("No PublishSubscribeChannel found among components of " +
            "logTrackingFlow: %s", logTrackingFlow.getIntegrationComponents().keySet()
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
    String logPath = normalizePath(tailingEvent.getFile().getAbsolutePath());
    Set<String> watchersFlowIds = sendingRegistry.get(logPath);
    assert (watchersFlowIds != null)
        : String.format("No watching flow ID found in registry by logPath='%s'.", logPath);
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
