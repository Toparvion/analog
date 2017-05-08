package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import ru.ftc.upc.testing.analog.model.config.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.config.ChoiceProperties;
import ru.ftc.upc.testing.analog.model.config.LogConfigEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

/**
 * @author Toparvion
 * @since v0.7
 */
abstract class AbstractWatchHandler extends ChannelInterceptorAdapter implements ExecutorChannelInterceptor {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ChoiceProperties choiceProperties;
  /**
   * This structure helps to prevent unnecessary work both on watching start and stop. On start it defends from
   * multiple registrations from the same node. On stop it prevents from unregistering of watching that is still used
   * by other clients of the same node. The elements are as follows: <br/>
   * {@code uid -> number of clients watching for log config entry with that uid }*/
  static final Map<String, AtomicInteger> clientCounters = new ConcurrentHashMap<>();

  protected AbstractWatchHandler(ChoiceProperties choiceProperties) {
    this.choiceProperties = choiceProperties;
  }

  @Override
  public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
    MessageHeaders headers = message.getHeaders();
    // 0. First of all let's filter out all excess invocations (those are not interesting in this context)
    SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(headers);
    if (!getTargetMessageType().equals(messageType)) {
      // in order to avoid multiple triggering this method passes invocations with message type other than SUBSCRIBE
      return message;
    }
    if (!SimpleBrokerMessageHandler.class.isAssignableFrom(handler.getClass())) {
      // in order to avoid multiple triggering this method passes invocations with handler other than for broker
      return message;
    }

    return beforeHandleInternal(message, channel, (SimpleBrokerMessageHandler) handler);
  }

  protected abstract SimpMessageType getTargetMessageType();

  protected abstract Message<?> beforeHandleInternal(Message<?> message,
                                                     MessageChannel channel,
                                                     SimpleBrokerMessageHandler handler);

  @Override
  public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) { }

  LogConfigEntry findMatchingLogConfigEntry(String uid) {
    LogConfigEntry matchingEntry = choiceProperties.getChoices().stream()
        .flatMap(choiceGroup -> choiceGroup.getLogs().stream())
        .filter(entry -> entry.getUid().equals(uid))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(format("No log configuration entry found for uid=%s", uid)));
    log.debug("Found matching log config entry: {}", matchingEntry);
    return matchingEntry;
  }

  String buildFullPath(LogConfigEntry matchingEntry) {
    String groupPathBase = choiceProperties.getChoices().stream()
        .filter(group -> group.getPathBase() != null)
        .filter(group -> group.getLogs().contains(matchingEntry))
        .findAny()
        .map(ChoiceGroup::getPathBase)
        .orElse("");
    return groupPathBase + matchingEntry.getPath();
  }

  String getUid(Message<?> message) {
    String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
    return subscriptionId.substring(subscriptionId.lastIndexOf('-')+1);
  }
}
