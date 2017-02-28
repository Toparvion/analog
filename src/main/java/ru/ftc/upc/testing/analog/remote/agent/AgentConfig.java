package ru.ftc.upc.testing.analog.remote.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.integration.router.HeaderValueRouter;

import java.net.InetSocketAddress;

import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static ru.ftc.upc.testing.analog.remote.CommonTrackingConstants.*;

/**
 * Spring configuration beans that compose tracking flow on the agent side.<p>
 * Created by Toparvion on 27.02.2017.
 */
@Configuration
@IntegrationComponentScan
public class AgentConfig {

  @Value("${remote.incoming.port:21003}")
  private int port;

  @Bean
  public IntegrationFlow agentRmiRegisteringFlow(TrackingService trackingService) {
    DirectChannel registerRmiInChannel = direct(AGENT_REGISTRATION_RMI_IN__CHANNEL).get();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(registerRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(port);

    return IntegrationFlows
        .from(inboundRmiGateway)
        .log()
        .<Boolean, HeaderValueRouter>
            route(new HeaderValueRouter(REGISTRATION_MODE__HEADER),
            routerSpec -> routerSpec
                .subFlowMapping(true  /* registering*/,
                    f -> f.handle(String.class, (logPath, headers) -> {
                      trackingService.registerWatcher((InetSocketAddress) headers.get(SENDER_ADDRESS__HEADER),
                          logPath,
                          (String) headers.get(LOG_TIMESTAMP_FORMAT__HEADER));
                      return null;    // just to conform GenericHandler interface
                    }))
                .subFlowMapping(false /* unregistering */,
                    f -> f.handle(String.class, (logPath, headers) -> {
                      trackingService.unregisterWatcher((InetSocketAddress) headers.get(SENDER_ADDRESS__HEADER),
                          logPath);
                      return null;    // just to conform GenericHandler interface
                    })),
            endpointSpec -> endpointSpec.id("registrationRouter"))
        .get();
  }


}
