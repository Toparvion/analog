package ru.ftc.upc.testing.analog.remote.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

  @Bean
  public ThreadPoolTaskScheduler heartbeatTaskScheduler() {
    // The following task scheduler is needed to enable heartbeats between server and client in order to detect
    // abnormal termination of either
    ThreadPoolTaskScheduler heartbeatTaskScheduler = new ThreadPoolTaskScheduler();
    heartbeatTaskScheduler.setThreadNamePrefix("websocketHeartbeatTaskScheduler-");
    return heartbeatTaskScheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker(WEBSOCKET_TOPIC_PREFIX)
          .setTaskScheduler(heartbeatTaskScheduler());
    config.setApplicationDestinationPrefixes(WEBSOCKET_APP_PREFIX);
  }

  @Override
  public void registerStompEndpoints(@SuppressWarnings("NullableProblems") StompEndpointRegistry registry) {
    registry.addEndpoint(WEBSOCKET_ENDPOINT)
            .withSockJS();
  }
}