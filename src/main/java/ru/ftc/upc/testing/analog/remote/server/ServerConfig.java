package ru.ftc.upc.testing.analog.remote.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.rmi.RmiInboundGateway;
import ru.ftc.upc.testing.analog.model.config.ClusterProperties;

import static java.lang.String.format;
import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

/**
 * Spring configuration bean that composes tracking flow on the server side.
 *
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@IntegrationComponentScan
public class ServerConfig {

  @Bean
  public IntegrationFlow serverRmiPayloadFlow(ClusterProperties clusterProperties, RecordSender sender) {
    DirectChannel payloadRmiInChannel = direct(SERVER_RMI_PAYLOAD_IN__CHANNEL).get();
    int myPort = clusterProperties.getMyselfNode().getPort();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(payloadRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host); // this causes application failure at startup due to connection refused
    inboundRmiGateway.setRegistryPort(myPort);
    inboundRmiGateway.setExpectReply(false);    // to avoid 1 sec delay on every request/response exchange

    return IntegrationFlows
        .from(inboundRmiGateway)
        .handle(sender::sendRecord)
        .get();
  }

  @Bean
  public IntegrationFlow serverRegistrationRouter() {
    /* Because the number and names of cluster nodes are not fixed, it is impossible to declare separate integration
    flows for every one of them. Therefore, the flows are created separately and dynamically. But this brings another
    problem - how to address them during messages dispatching? To solve this problem the input channel of every
    integration flow is named by format "SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX + nodeName". The first one is
    a constant and the second is given as corresponding message's payload field. The following intermediate flow uses
    SpEL to extract the field value, combine it with the constant value and thus define the channel name to redirect
    the message to. */
    return IntegrationFlows.from(direct(SERVER_REGISTRATION_ROUTER__CHANNEL))
        .route(format("'%s'.concat(payload.nodeName)", SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX))
        .get();
  }

}
