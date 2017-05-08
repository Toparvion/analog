package ru.ftc.upc.testing.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowRegistration;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.util.timestamp.TimestampExtractor;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  private final Map<String, Set<AddressAwareRmiOutboundGateway>> watchersRegistry = new HashMap<>();

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
   *
   * @param watcherAddress
   * @param logPath
   * @param timestampFormat
   */
  void registerWatcher(InetSocketAddress watcherAddress, String logPath, String timestampFormat) {
    log.info("Получен запрос на регистрацию наблюдателя {} за логом '{}' (формат метки: {}).",
              watcherAddress, logPath, timestampFormat);

    // first let's check if it is a duplicate registration request
    Set<AddressAwareRmiOutboundGateway> knownWatchers = watchersRegistry.get(logPath);
    if (knownWatchers != null && knownWatchers.stream()
        .map(AddressAwareRmiOutboundGateway::getGatewayAddress)
        .anyMatch(knownAddress -> knownAddress.equals(watcherAddress))) {
      log.warn("Watcher {} is already registered for log '{}'.", watcherAddress, logPath);
      return;
    }

    // the watcher wasn't registered yet; we may go on
    StandardIntegrationFlow logTrackingFlow;
    String logTrackingRegistrationId = logTrackingRegistry.get(logPath);
    if (logTrackingRegistrationId != null) {
      IntegrationFlowRegistration logTrackingRegistration = flowContext.getRegistrationById(logTrackingRegistrationId);
      logTrackingFlow = (StandardIntegrationFlow) logTrackingRegistration.getIntegrationFlow();
      log.info("Найдено существующее слежение с id={}.", logTrackingRegistrationId);
//      log.info("Found existing log tracking flow with id={}.", logTrackingRegistrationId);

    } else {
      IntegrationFlowRegistration registration = flowContext
          .registration(tailingFlowProvider.provideTailingFlow(logPath))
          .autoStartup(true)
          .register();
      logTrackingRegistry.put(logPath, registration.getId());
      logTrackingFlow = (StandardIntegrationFlow) registration.getIntegrationFlow();
      timestampExtractor.registerNewTimestampFormat(timestampFormat, logPath);
      log.info("Создано новое слежение для лога '{}' с id={}.", logPath, registration.getId());
//      log.info("Created new log tracking flow with id={}.", registration.getId());
    }

    PublishSubscribeChannel outChannel = extractOutChannel(logTrackingFlow, logTrackingRegistrationId);

    String payloadSendingUrl = String.format("rmi://%s:%d/%s%s",
        watcherAddress.getHostName(),
        watcherAddress.getPort(),
        RmiInboundGateway.SERVICE_NAME_PREFIX,
        SERVER_RMI_PAYLOAD_IN__CHANNEL);
    AddressAwareRmiOutboundGateway payloadOutGateway = new AddressAwareRmiOutboundGateway(
        watcherAddress,
        payloadSendingUrl);

    outChannel.subscribe(payloadOutGateway);

    watchersRegistry.computeIfAbsent(logPath, s -> new HashSet<>())
                    .add(payloadOutGateway);
    log.debug("Зарегистрирован новый слушатель лога: {}", payloadOutGateway.getGatewayAddress());
//    log.debug("Registered new payloadOutGateway with address {}", payloadOutGateway.getGatewayAddress());
  }

  /**
   *
   * @param watcherAddress
   * @param logPath
   */
  void unregisterWatcher(InetSocketAddress watcherAddress, String logPath) {
    log.info("Получен запрос на дерегистрацию наблюдателя {} для лога: '{}'", watcherAddress, logPath);

    // находим соответствующее слежение и извлекаем из него выходной канал
    String trackingLogFlowId = logTrackingRegistry.get(logPath);
    assert (trackingLogFlowId != null) : "No trackingLogFlowId found for logPath="+logPath;
    IntegrationFlowRegistration trackingLogRegistration = flowContext.getRegistrationById(trackingLogFlowId);
    assert trackingLogRegistration != null;
    StandardIntegrationFlow trackingLogFlow = (StandardIntegrationFlow) trackingLogRegistration.getIntegrationFlow();
    PublishSubscribeChannel outChannel = extractOutChannel(trackingLogFlow, trackingLogFlowId);

    // находим соответствующего наблюдателя
    Set<AddressAwareRmiOutboundGateway> registeredWatchers = watchersRegistry.get(logPath);
    assert registeredWatchers != null;
    AddressAwareRmiOutboundGateway registeredWatcher = registeredWatchers.stream()
        .filter(watcher -> watcher.getGatewayAddress().equals(watcherAddress))
        .findAny()
        .orElseThrow(IllegalStateException::new);

    // отписываем наблюдателя от канала
    outChannel.unsubscribe(registeredWatcher);
    registeredWatchers.remove(registeredWatcher);
    log.debug("Наблюдатель '{}' отписан от канала '{}'.", registeredWatcher, outChannel);
//    log.debug("Watcher '{}' has been unsubscribed from channel '{}'.", registeredWatcher, outChannel);

    if (registeredWatchers.isEmpty()) {
      flowContext.remove(trackingLogFlowId);
      logTrackingRegistry.remove(logPath);
      log.debug("Для лога '{}' не осталось наблюдателей. Слежение прекращено.", logPath);
//      log.debug("There is no watchers for log {} anymore. Tracking removed.", logPath);
    }
  }

  @EventListener
  public void processFileTailingEvent(FileTailingMessageProducerSupport.FileTailingEvent event) {
    log.debug("Caught file tailing event: {}", event.toString());
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow, String registrationId) {
    return logTrackingFlow.getIntegrationComponents()
        .stream()
        .filter(component -> PublishSubscribeChannel.class.isAssignableFrom(component.getClass()))
        .findAny()
        .map(component -> (PublishSubscribeChannel) component)
        .orElseThrow(() -> new IllegalStateException(String.format("A logTrackingFlow for regId=%s is found " +
            "but it doesn't contain PublishSubscribeChannel.", registrationId)));
  }

}
