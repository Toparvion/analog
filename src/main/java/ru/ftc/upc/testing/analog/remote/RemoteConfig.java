package ru.ftc.upc.testing.analog.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.integration.rmi.RmiOutboundGateway;
import org.springframework.integration.router.HeaderValueRouter;

import java.net.InetSocketAddress;

import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static org.springframework.integration.rmi.RmiInboundGateway.SERVICE_NAME_PREFIX;

/**
 * Created by Toparvion on 15.01.2017.
 */
@Configuration
@IntegrationComponentScan
public class RemoteConfig {
  private static final Logger log = LoggerFactory.getLogger(RemoteConfig.class);
  static final String PAYLOAD_RMI_IN_CHANNEL_ID = "payloadRmiInChannel";

  static final String REGISTER_RMI_OUT_CHANNEL_ID = "registerRmiOutChannel";
  static final String REGISTRATION_MODE_HEADER_NAME = "isRegistration";

  private static final String REGISTER_RMI_IN_CHANNEL_ID = "registerRmiInChannel";
  private static final String SENDER_ADDRESS_HEADER_NAME = "senderAddress";

  static final String LOG_TIMESTAMP_HEADER_NAME = "logTimestamp";

  @Value("${remote.incoming.address:127.0.0.1}")
  private String host;
  @Value("${remote.incoming.port:21003}")
  private int port;

  @Bean
  public IntegrationFlow registerRmiInFlow(RemotingService remotingService) {
    DirectChannel registerRmiInChannel = direct(REGISTER_RMI_IN_CHANNEL_ID).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(registerRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(port);

    return IntegrationFlows
        .from(inboundRmiGateway)
        .log()
        .<Boolean, HeaderValueRouter>
            route(new HeaderValueRouter(REGISTRATION_MODE_HEADER_NAME),
              routerSpec -> routerSpec
                  .subFlowMapping(true  /* registering*/,
                      f -> f.handle(String.class, (logPath, headers) -> {
                        remotingService.registerWatcher((InetSocketAddress) headers.get(SENDER_ADDRESS_HEADER_NAME),
                                                        logPath,
                                                        (String) headers.get(LOG_TIMESTAMP_HEADER_NAME));
                        return null;
                      }))
                  .subFlowMapping(false /* unregistering */,
                      f -> f.handle(String.class, (logPath, headers) -> {
                        remotingService.unregisterWatcher((InetSocketAddress) headers.get(SENDER_ADDRESS_HEADER_NAME), logPath);
                        return null;
                      })),
            endpointSpec -> endpointSpec.id("registrationRouter"))
        .get();
  }

  @Bean
  public IntegrationFlow registerRmiOutFlow() {
    String rmiUrl = String.format("rmi://%s:%d/%s%s",
        host,
        port,
        SERVICE_NAME_PREFIX,
        REGISTER_RMI_IN_CHANNEL_ID);
    log.debug("Creating registration RMI outbound gateway with URL: {}", rmiUrl);

    return IntegrationFlows
        .from(direct(REGISTER_RMI_OUT_CHANNEL_ID))
        .enrichHeaders(e -> e.header(SENDER_ADDRESS_HEADER_NAME, new InetSocketAddress(host, port)))
        .handle(new RmiOutboundGateway(rmiUrl))
        .get();
  }

  @Bean
  public IntegrationFlow payloadRmiInFlow() {
    DirectChannel payloadRmiInChannel = direct(PAYLOAD_RMI_IN_CHANNEL_ID).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(payloadRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host); // this causes application failure at startup due to connection refused
    inboundRmiGateway.setRegistryPort(port);

    return IntegrationFlows
        .from(inboundRmiGateway)
        .handle(message -> log.info("Возвращена строка: '{}'", message.getPayload()))
        .get();
  }

}
