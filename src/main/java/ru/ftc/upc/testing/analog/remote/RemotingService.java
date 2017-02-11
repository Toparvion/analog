package ru.ftc.upc.testing.analog.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowRegistration;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.integration.rmi.RmiOutboundGateway;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.integration.file.dsl.Files.tailAdapter;
import static ru.ftc.upc.testing.analog.remote.RemoteConfig.LOG_TIMESTAMP_HEADER_NAME;

/**
 * Created by Toparvion on 15.01.2017.
 */
@Service
public class RemotingService {
  private static final Logger log = LoggerFactory.getLogger(RemotingService.class);

  /**
   * The registry of logs being tracked, where the key is log path being watched and the value is corresponding
   * {@link IntegrationFlowRegistration} id.
   */
  private final Map<String, String> logTrackingRegistry = new HashMap<>();

  private final Map<String, Set<AddressAwareRmiOutboundGateway>> watchersRegistry = new HashMap<>();

  private IntegrationFlowContext flowContext;
  private final TimestampExtractor timestampExtractor;

  @Autowired
  public RemotingService(@SuppressWarnings("SpringJavaAutowiringInspection") IntegrationFlowContext flowContext,
                         TimestampExtractor timestampExtractor) {
    this.flowContext = flowContext;
    this.timestampExtractor = timestampExtractor;
  }

  void registerWatcher(InetSocketAddress watcherAddress, String logPath, String timestampFormat) {
    log.info("Получен запрос на регистрацию наблюдателя {} за логом '{}' (формат метки: {}).",
              watcherAddress, logPath, timestampFormat);

    // first let's check if it is duplicate registration request
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
          .registration(tailingFlow(logPath))
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
        RemoteConfig.PAYLOAD_RMI_IN_CHANNEL_ID);
    AddressAwareRmiOutboundGateway payloadOutGateway = new AddressAwareRmiOutboundGateway(
        watcherAddress,
        payloadSendingUrl);

    outChannel.subscribe(payloadOutGateway);

    watchersRegistry.computeIfAbsent(logPath, s -> new HashSet<>())
                    .add(payloadOutGateway);
    log.debug("Зарегистрирован новый слушатель лога: {}", payloadOutGateway.getGatewayAddress());
//    log.debug("Registered new payloadOutGateway with address {}", payloadOutGateway.getGatewayAddress());
  }

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

  private IntegrationFlow tailingFlow(String logPath) {
    return IntegrationFlows
        .from(tailAdapter((new File(logPath)))
            .delay(1000)
            .end(true)
            .reopen(false))
        .enrichHeaders(e -> e.headerFunction(LOG_TIMESTAMP_HEADER_NAME, timestampExtractor::extractTimestamp))
        .channel(channels -> channels.publishSubscribe(logPath))
        .get();
  }

  @EventListener
  public void processFileTailingEvent(FileTailingMessageProducerSupport.FileTailingEvent event) {
    log.debug("Caught file tailing event: {}", event.toString());
  }

  private PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow, String registrationId) {
    return logTrackingFlow.getIntegrationComponents()
        .stream()
        .filter(component -> PublishSubscribeChannel.class.isAssignableFrom(component.getClass()))
        .map(component -> (PublishSubscribeChannel) component)
        .findAny()
        .orElseThrow(() -> new IllegalStateException(String.format("A logTrackingFlow for regId=%s is found " +
            "but it doesn't contain PublishSubscribeChannel.", registrationId)));
  }

  private class AddressAwareRmiOutboundGateway extends RmiOutboundGateway {

    private final InetSocketAddress gatewayAddress;

    AddressAwareRmiOutboundGateway(InetSocketAddress address, String url) {
      super(url);
      this.gatewayAddress = address;
    }

    InetSocketAddress getGatewayAddress() {
      return gatewayAddress;
    }
  }

}
