package ru.ftc.upc.testing.analog.remote.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import static ru.ftc.upc.testing.analog.remote.RemotingConstants.WEBSOCKET_TOPIC_PREFIX;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

  private final StartWatchHandler startWatchHandler;
  private final StopWatchHandler stopWatchHandler;

  @Autowired
  public WebSocketConfig(StartWatchHandler startWatchHandler, StopWatchHandler stopWatchHandler) {
    this.startWatchHandler = startWatchHandler;
    this.stopWatchHandler = stopWatchHandler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    taskScheduler.initialize();
    config.enableSimpleBroker(WEBSOCKET_TOPIC_PREFIX)
          .setTaskScheduler(taskScheduler);
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(@SuppressWarnings("NullableProblems") StompEndpointRegistry registry) {
    registry.addEndpoint("/watch-endpoint")
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(startWatchHandler, stopWatchHandler);
  }
}