package tech.toparvion.analog.remote.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.rmi.RmiInboundGateway;
import tech.toparvion.analog.model.config.nodes.NodesProperties;
import tech.toparvion.analog.model.remote.TrackingRequest;

import java.net.InetSocketAddress;

import static org.springframework.integration.dsl.MessageChannels.direct;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 * Spring configuration bean from which the tracking logic originates (agent side).
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@IntegrationComponentScan
public class AgentConfig {

  @Bean
  public IntegrationFlow agentRmiRegisteringFlow(TrackingService trackingService, NodesProperties nodesProperties) {
    DirectChannel registerRmiInChannel = direct(AGENT_REGISTRATION_RMI_IN__CHANNEL).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(registerRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(nodesProperties.getThis().getAgentPort());

    return IntegrationFlows
        .from(inboundRmiGateway)
        .log()
        .route("headers." + REGISTRATION_MODE__HEADER,
            spec -> spec
                .subFlowMapping(true  /* registering*/,
                    f -> f.handle(TrackingRequest.class, (request, headers) -> {
                      trackingService.registerWatcher(request, (InetSocketAddress) headers.get(REPLY_ADDRESS__HEADER));
                      return null;    // just to conform GenericHandler interface
                    }))
                .subFlowMapping(false /* unregistering */,
                    f -> f.handle(TrackingRequest.class, (request, headers) -> {
                      trackingService.unregisterWatcher(request, (InetSocketAddress) headers.get(REPLY_ADDRESS__HEADER));
                      return null;    // just to conform GenericHandler interface
                    }))
                .id("agentRegistrationRouter"))
        .get();
  }


}
