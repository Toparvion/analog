package ru.ftc.upc.testing.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowRegistration;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.model.TrackingRequest;
import ru.ftc.upc.testing.analog.util.timestamp.TimestampExtractor;

import java.net.InetSocketAddress;
import java.util.*;

import static java.lang.String.format;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.LOG_CONFIG_ENTRY_UID__HEADER;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.SERVER_RMI_PAYLOAD_IN__CHANNEL;

/**
 * Applied logical service providing routines for remote log tracking.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class TrackingService {
  private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

  /**
   * The registry of logs being tracked, where the key is log path being watched and the value is corresponding
   * {@link IntegrationFlowRegistration} id.
   */
  private final Map<String, String> logTrackingRegistry = new HashMap<>();

  /**
   * The registry of watchers' gateways: log path -> set of payload outbound gateway flow ids
   */
  private final Map<String, Set<String>> watchersRegistry = new HashMap<>();

  private IntegrationFlowContext flowContext;
  private final TimestampExtractor timestampExtractor;
  private final TailingFlowProvider tailingFlowProvider;


  @Autowired
  public TrackingService(@SuppressWarnings("SpringJavaAutowiringInspection") IntegrationFlowContext flowContext,
                         TimestampExtractor timestampExtractor,
                         TailingFlowProvider tailingFlowProvider) {
    this.flowContext = flowContext;
    this.timestampExtractor = timestampExtractor;
    this.tailingFlowProvider = tailingFlowProvider;
  }

  /**
   * Initiates tracking process for the log specified in {@code request}: <ol>
   *   <li>Checks if this is a duplicate registration (the request for the same log came from the same watcher);</li>
   *   <li>Finds or {@link TailingFlowProvider#provideTailingFlow(java.lang.String) creates} tailing flow (alongside
   *   with the {@link TimestampExtractor#registerNewTimestampFormat(java.lang.String, java.lang.String) registration}
   *   of the specified timestamp format);</li>
   *   <li>Creates new {@code RmiOutboundGateway} capable of sending messages to the {@code watcherAddress} and makes
   *   it a subscriber for the tailing flow output channel;</li>
   *   <li>Stores the gateway in a {@link TrackingService#watchersRegistry registry} in order to find it when turning
   *   the tracking off.</li>
   * </ol>
   * @param request holder of parameters for tracking creation
   * @param watcherAddress address of the node to which the tracked messages should be sent
   */
  void registerWatcher(TrackingRequest request, InetSocketAddress watcherAddress) {
    log.info("Получен запрос на регистрацию наблюдателя {} за логом {}.", watcherAddress, request);
    String logPath = request.getLogFullPath();
    String timestampFormat = request.getTimestampFormat();

    // first let's check if it is a duplicate registration request
    Set<String> knownWatcherFlowIds = watchersRegistry.get(logPath);
    if (knownWatcherFlowIds != null && knownWatcherFlowIds.stream()
        .map(this::findGateway)
        .map(AddressAwareRmiOutboundGateway::getGatewayAddress)
        .anyMatch(knownAddress -> knownAddress.equals(watcherAddress))) {
      log.warn("Watcher {} is already registered for log '{}'.", watcherAddress, logPath);
      return;
    }

    // the watcher wasn't registered yet; we may go on
    StandardIntegrationFlow logTrackingFlow;
    IntegrationFlowRegistration tailingReg = null;
    String logTrackingRegistrationId = logTrackingRegistry.get(logPath);
    if (logTrackingRegistrationId != null) {
      IntegrationFlowRegistration logTrackingRegistration = flowContext.getRegistrationById(logTrackingRegistrationId);
      logTrackingFlow = (StandardIntegrationFlow) logTrackingRegistration.getIntegrationFlow();
      log.info("Найдено существующее слежение с id={}.", logTrackingRegistrationId);
//      log.info("Found existing log tracking flow with id={}.", logTrackingRegistrationId);

    } else {
      tailingReg = flowContext
          .registration(tailingFlowProvider.provideTailingFlow(logPath))
          .autoStartup(false)
          .register();
      logTrackingRegistry.put(logPath, tailingReg.getId());
      logTrackingFlow = (StandardIntegrationFlow) tailingReg.getIntegrationFlow();
      timestampExtractor.registerNewTimestampFormat(timestampFormat, logPath);
      log.info("Создано новое слежение для лога '{}' с id={}.", logPath, tailingReg.getId());
//      log.info("Created new log tracking flow with id={}.", registration.getId());
    }

    PublishSubscribeChannel outChannel = extractOutChannel(logTrackingFlow, logTrackingRegistrationId);

    String payloadSendingUrl = format("rmi://%s:%d/%s%s",
        watcherAddress.getHostName(),
        watcherAddress.getPort(),
        RmiInboundGateway.SERVICE_NAME_PREFIX,
        SERVER_RMI_PAYLOAD_IN__CHANNEL);
    AddressAwareRmiOutboundGateway payloadOutGateway = new AddressAwareRmiOutboundGateway(watcherAddress, payloadSendingUrl);

    IntegrationFlowRegistration broadcastReg = flowContext.registration(
        IntegrationFlows
            .from(outChannel)
            .enrichHeaders(e -> e.header(LOG_CONFIG_ENTRY_UID__HEADER, request.getUid()))
            .handle(payloadOutGateway)
            .get())
        .autoStartup(false)
        .register();

    watchersRegistry.computeIfAbsent(logPath, s -> new HashSet<>())
                    .add(broadcastReg.getId());
    // now that broadcasting facilities are set up we may allow the flows to run
    broadcastReg.start();
    if (tailingReg != null) {
      tailingReg.start();
    }
    log.debug("Зарегистрирован новый слушатель лога: {} (регистрация: '{}').", payloadOutGateway.getGatewayAddress(),
        broadcastReg.getId());
//    log.debug("Registered new payloadOutGateway with address {}", payloadOutGateway.getGatewayAddress());
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
    String trackingLogFlowId = logTrackingRegistry.get(logPath);
    assert (trackingLogFlowId != null) : "No trackingLogFlowId found for logPath="+logPath;
    IntegrationFlowRegistration trackingLogRegistration = flowContext.getRegistrationById(trackingLogFlowId);
    assert trackingLogRegistration != null;
    StandardIntegrationFlow trackingLogFlow = (StandardIntegrationFlow) trackingLogRegistration.getIntegrationFlow();
    PublishSubscribeChannel outChannel = extractOutChannel(trackingLogFlow, trackingLogFlowId);

    // находим соответствующего наблюдателя
    Set<String> watchersFlowIds = watchersRegistry.get(logPath);
    assert watchersFlowIds != null;
    String registeredFlowId = watchersFlowIds.stream()
        .filter(flowId -> findGateway(flowId).getGatewayAddress().equals(watcherAddress))
        .findAny()
        .orElseThrow(IllegalStateException::new);

    // отписываем наблюдателя от канала
    flowContext.remove(registeredFlowId);
    watchersFlowIds.remove(registeredFlowId);
    log.debug("Процесс слежения с регистрацией id='{}' отписан от канала '{}'.", registeredFlowId, outChannel);
//    log.debug("Watcher '{}' has been unsubscribed from channel '{}'.", registeredWatcher, outChannel);

    if (watchersFlowIds.isEmpty()) {
      flowContext.remove(trackingLogFlowId);
      logTrackingRegistry.remove(logPath);
      log.debug("Для лога '{}' не осталось наблюдателей. Слежение прекращено.", logPath);
//      log.debug("There is no watchers for log {} anymore. Tracking removed.", logPath);
    }
  }

  @EventListener
  public void processFileTailingEvent(FileTailingMessageProducerSupport.FileTailingEvent event) {
    log.info("Caught file tailing event: {}", event.toString());
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow, String registrationId) {
    return logTrackingFlow.getIntegrationComponents()
        .stream()
        .filter(component -> PublishSubscribeChannel.class.isAssignableFrom(component.getClass()))
        .findAny()
        .map(component -> (PublishSubscribeChannel) component)
        .orElseThrow(() -> new IllegalStateException(format("A logTrackingFlow for regId=%s is found " +
            "but it doesn't contain PublishSubscribeChannel.", registrationId)));
  }

  private AddressAwareRmiOutboundGateway findGateway(String flowId) {
    IntegrationFlowRegistration registration = flowContext.getRegistrationById(flowId);
    assert registration != null;
    StandardIntegrationFlow flow = (StandardIntegrationFlow) registration.getIntegrationFlow();
    List<Object> components = flow.getIntegrationComponents();
    Object lastComponent = components.get(components.size() - 1); // ConsumerEndpointFactoryBean
    ConfigurablePropertyAccessor propertyAccessor = PropertyAccessorFactory.forDirectFieldAccess(lastComponent);
    return  (AddressAwareRmiOutboundGateway) propertyAccessor.getPropertyValue("handler");
  }

}
