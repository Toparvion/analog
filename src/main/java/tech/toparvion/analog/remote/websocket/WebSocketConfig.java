package tech.toparvion.analog.remote.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import tech.toparvion.analog.infra.graph.WebSocketSessionListener;
import tech.toparvion.analog.infra.graph.repo.ClientRepository;

import javax.annotation.Nonnull;

import static tech.toparvion.analog.remote.RemotingConstants.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final ClientRepository clientRepository;

  @Autowired
  public WebSocketConfig(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  @Bean
  public ThreadPoolTaskScheduler heartbeatTaskScheduler() {
    // The following task scheduler is needed to enable heartbeats between server and client in order to detect
    // abnormal termination of either
    ThreadPoolTaskScheduler heartbeatTaskScheduler = new ThreadPoolTaskScheduler();
    heartbeatTaskScheduler.setThreadNamePrefix("websocketHeartbeatTaskScheduler-");
    return heartbeatTaskScheduler;
  }

  @Override
  public void configureMessageBroker(@Nonnull MessageBrokerRegistry config) {
    config.enableSimpleBroker(WEBSOCKET_TOPIC_PREFIX)
          .setTaskScheduler(heartbeatTaskScheduler());
    config.setApplicationDestinationPrefixes(WEBSOCKET_APP_PREFIX);
  }

  @Override
  public void registerStompEndpoints(@Nonnull StompEndpointRegistry registry) {
    registry.addEndpoint(WEBSOCKET_ENDPOINT)
            .withSockJS();
  }

  @Override
  public void configureWebSocketTransport(@Nonnull WebSocketTransportRegistration registry) {
    registry.addDecoratorFactory(delegate -> new WebSocketSessionListener(delegate, clientRepository));
    // tip: turn out the previous expression into anonymous class to understand what it is really composed of
  }
}