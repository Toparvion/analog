package ru.ftc.upc.testing.analog.remote.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.rmi.RmiInboundGateway;
import ru.ftc.upc.testing.analog.model.config.ClusterProperties;

import java.net.InetSocketAddress;

import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

/**
 * Spring configuration bean that compose tracking flow on the agent side.<p>
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@IntegrationComponentScan
public class AgentConfig {

  @Bean
  public IntegrationFlow agentRmiRegisteringFlow(TrackingService trackingService, ClusterProperties clusterProperties) {
    DirectChannel registerRmiInChannel = direct(AGENT_REGISTRATION_RMI_IN__CHANNEL).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(registerRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(clusterProperties.getMyselfNode().getPort());

    return IntegrationFlows
        .from(inboundRmiGateway)
        .log()
        .route("headers." + REGISTRATION_MODE__HEADER,
            spec -> spec
                .subFlowMapping(true  /* registering*/,
                    f -> f.handle(String.class, (logPath, headers) -> {
                      trackingService.registerWatcher((InetSocketAddress) headers.get(REPLY_ADDRESS__HEADER),
                          logPath,
                          (String) headers.get(LOG_TIMESTAMP_FORMAT__HEADER));
                      return null;    // just to conform GenericHandler interface
                    }))
                .subFlowMapping(false /* unregistering */,
                    f -> f.handle(String.class, (logPath, headers) -> {
                      trackingService.unregisterWatcher((InetSocketAddress) headers.get(REPLY_ADDRESS__HEADER),
                          logPath);
                      return null;    // just to conform GenericHandler interface
                    }))
                .id("agentRegistrationRouter"))
        .get();
  }


}
