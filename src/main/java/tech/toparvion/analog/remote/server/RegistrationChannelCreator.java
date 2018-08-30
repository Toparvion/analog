package tech.toparvion.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.rmi.RmiOutboundGateway;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.model.config.ClusterNode;
import tech.toparvion.analog.model.config.ClusterProperties;

import static java.lang.String.format;
import static org.springframework.integration.dsl.MessageChannels.direct;
import static org.springframework.integration.rmi.RmiInboundGateway.SERVICE_NAME_PREFIX;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 * Other cluster nodes may be unavailable upon starting current one. Furthermore they may theoretically change their
 * addresses over time. To handle such a behavior the process of creating channels for tracking registration is made
 * lazy. Combining with Eureka this would allow to build truly adaptive application.<p>
 * The only channel that is created during application startup is channel to itself.
 * @author Toparvion
 * @since v0.7
 */
@Component
public class RegistrationChannelCreator implements BeanFactoryAware, InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(RegistrationChannelCreator.class);

  private final ClusterProperties clusterProperties;
  private final IntegrationFlowContext dynamicRegistrar;
  private BeanFactory beanFactory;

  @Autowired
  public RegistrationChannelCreator(
      ClusterProperties clusterProperties,
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
          IntegrationFlowContext dynamicRegistrar) {
    this.clusterProperties = clusterProperties;
    this.dynamicRegistrar = dynamicRegistrar;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }

  /**
   * Checks whether channel for specified {@code nodeName} already exists in Spring context and, if not, creates it.
   * @param nodeName name of cluster node to which the channel should be created
   */
  public void createRegistrationChannelIfNeeded(String nodeName) {
    try {
      beanFactory.getBean(SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX + nodeName);

    } catch (NoSuchBeanDefinitionException e) {
      ClusterNode targetNode = clusterProperties.findNodeByName(nodeName);
      createChannelTo(targetNode);
    }
  }

  private void createChannelTo(ClusterNode node) {
    String rmiUrl = format("rmi://%s:%d/%s%s",
        node.getHost(),
        node.getAgentPort(),
        SERVICE_NAME_PREFIX,
        AGENT_REGISTRATION_RMI_IN__CHANNEL);

    StandardIntegrationFlow serverRmiRegisteringFlow =
        IntegrationFlows
            .from(direct(SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX + node.getName()))
            .enrichHeaders(e -> e.header(REPLY_ADDRESS__HEADER, clusterProperties.getMyselfNode().getAgentInetSocketAddress()))
            .handle(new RmiOutboundGateway(rmiUrl))
            .get();

    dynamicRegistrar.registration(serverRmiRegisteringFlow)
        .autoStartup(true)
        .register();
    log.debug("Created registration RMI outbound gateway to node '{}' with URL: {}", node.getName(), rmiUrl);
  }

  @Override
  public void afterPropertiesSet() {
    ClusterNode myselfNode = clusterProperties.getMyselfNode();
    createChannelTo(myselfNode);
  }
}
