package ru.ftc.upc.testing.analog.remote.server;

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
import ru.ftc.upc.testing.analog.model.RecordLevel;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;

import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static org.springframework.integration.rmi.RmiInboundGateway.SERVICE_NAME_PREFIX;
import static ru.ftc.upc.testing.analog.remote.CommonTrackingConstants.*;

/**
 * Spring configuration beans that compose tracking flow on the server side.<p>
 * Created by Toparvion on 15.01.2017.
 */
@Configuration
@IntegrationComponentScan
public class ServerConfig {
  private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

  @Value("${remote.incoming.address:127.0.0.1}")
  private String host;
  @Value("${remote.incoming.port:21003}")
  private int port;


  @Bean
  public IntegrationFlow serverRmiRegisteringFlow() {
    String rmiUrl = String.format("rmi://%s:%d/%s%s",
        host,
        port,
        SERVICE_NAME_PREFIX,
        AGENT_REGISTRATION_RMI_IN__CHANNEL);
    log.debug("Creating registration RMI outbound gateway with URL: {}", rmiUrl);

    return IntegrationFlows
        .from(direct(SERVER_REGISTRATION_RMI_OUT__CHANNEL))
        .enrichHeaders(e -> e.header(SENDER_ADDRESS__HEADER, new InetSocketAddress(host, port)))
        .handle(new RmiOutboundGateway(rmiUrl))
        .get();
  }

  @Bean
  public IntegrationFlow serverRmiPayloadFlow() {
    DirectChannel payloadRmiInChannel = direct(SERVER_RMI_PAYLOAD_IN__CHANNEL).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(payloadRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host); // this causes application failure at startup due to connection refused
    inboundRmiGateway.setRegistryPort(port);
    inboundRmiGateway.setExpectReply(false);    // to avoid 1 sec delay on every request/response exchange

    return IntegrationFlows
        .from(inboundRmiGateway)
        .handle(message -> log.info("Получена запись с меткой {} и уровнем {}:\n< {}",
            message.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class),
            message.getHeaders().get(RECORD_LEVEL__HEADER, RecordLevel.class),
            message.getPayload().toString().replaceAll("\\n", "\n< ")))
        .get();
  }

}
