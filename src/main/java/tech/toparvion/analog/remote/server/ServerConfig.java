package tech.toparvion.analog.remote.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.integration.rmi.RmiInboundGateway;
import tech.toparvion.analog.model.config.nodes.NodesProperties;

import java.util.Collection;

import static java.lang.String.format;
import static org.springframework.integration.dsl.MessageChannels.direct;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 * Spring configuration bean that composes tracking flow on the server side.
 *
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@IntegrationComponentScan
@EnableIntegrationManagement(defaultCountsEnabled = "true", defaultStatsEnabled = "true")
public class ServerConfig {

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
        .route(format("'%s'.concat(payload.logPath.node)", SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX))
        .get();
  }

  @Bean
  public IntegrationFlow serverRmiPayloadFlow(NodesProperties nodesProperties,
                                              RecordSender recordSender,
                                              MetaDataSender metaDataSender) {
    DirectChannel payloadRmiInChannel = direct(SERVER_RMI_PAYLOAD_IN__CHANNEL).get();
    int myPort = nodesProperties.getThis().getAgentPort();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(payloadRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(myPort);
    inboundRmiGateway.setExpectReply(false);    // to avoid 1 sec delay on every request/response exchange

    return IntegrationFlows
        .from(inboundRmiGateway)
        .<Object, Class<?>>   // 'Object' stands for message payload; 'Class<?>' stands for payload type
            route(this::detectPayloadClass, routerSpec -> routerSpec
                .subFlowMapping(Collection.class, flow -> flow.handle(recordSender::sendRecord))
                .subFlowMapping(FileTailingEvent.class, flow -> flow.handle(metaDataSender::sendMetaData)))
        .get();
  }

  /**
   * Since records can arrive in various forms of collection, AnaLog needs a way to generalize it. Otherwise the router
   * wouldn't be able to correctly perform the redirect because payload type router relies on trivial class name
   * comparison only. To address that issue this method detects and returns {@link Collection} type for any
   * appropriate message payload.
   * @return message payload type to base the routing on
   */
  private Class<?> detectPayloadClass(Object messagePayload) {
    return Collection.class.isAssignableFrom(messagePayload.getClass())
        ? Collection.class
        : messagePayload.getClass();
  }

}
