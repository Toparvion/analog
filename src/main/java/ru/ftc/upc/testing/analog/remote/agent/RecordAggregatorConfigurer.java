package ru.ftc.upc.testing.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.AggregatorSpec;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.math.BigDecimal;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.messaging.support.MessageBuilder.withPayload;

/**
 * @author Toparvion
 * @since v0.7
 */
class RecordAggregatorConfigurer {
  private static final Logger log = LoggerFactory.getLogger(RecordAggregatorConfigurer.class);

  private final MessageChannel preAggregatorQueueChannel;
  private final int groupSizeThreshold;
  private final long groupTimeout;

  RecordAggregatorConfigurer(MessageChannel preAggregatorQueueChannel, int groupSizeThreshold, long groupTimeout) {
    this.preAggregatorQueueChannel = preAggregatorQueueChannel;
    this.groupSizeThreshold = groupSizeThreshold;
    this.groupTimeout = groupTimeout;
  }

  void configure(AggregatorSpec spec) {
    spec.correlationStrategy(message -> BigDecimal.ONE)
        .releaseStrategy(this::releaseStrategy)
        .outputProcessor(this::outputProcessor)
        .groupTimeout(groupTimeout)
        .sendPartialResultOnExpiry(true)
        .expireGroupsUponCompletion(true)
        .expireGroupsUponTimeout(true)
        .poller(p -> p.fixedDelay(50, MILLISECONDS))
        .id("recordAggregator");
  }

  private boolean releaseStrategy(MessageGroup group) {
    assert group.size() > 0;
    if (group.size() == 1) {
      return false; // i.e. if group consists of just one element and thus is insufficient for making decision
    }
    if (group.size() >= groupSizeThreshold) {
      return true;
    }
    Tuple2<Message<?>, Message<?>> prevAndLastMessages = findPrevAndLastMessages(group);
    Message<?> lastMessage = prevAndLastMessages.getT1();
    Message<?> second2LastMessage = prevAndLastMessages.getT2();
    assert (lastMessage != null) && (second2LastMessage != null);   // relying on check for singleton group
    Long lastMessageCorrId = lastMessage.getHeaders().get(CORRELATION_ID, Long.class);
    Long prevMessageCorrId = second2LastMessage.getHeaders().get(CORRELATION_ID, Long.class);

    boolean isGroupComplete = !Objects.equals(lastMessageCorrId, prevMessageCorrId);
    if (isGroupComplete) {
      log.debug("Group {} is about to be released as last corrId {} differs from previous ones {}.",
          group.getGroupId(), lastMessageCorrId, prevMessageCorrId);
    }
    return isGroupComplete;
  }

  private Object outputProcessor(MessageGroup group) {
    assert group.size() > 0;
    if (group.size() == 1) {      // the singleton group is special case and must be handled separately
      return withPayload(singletonList(group.getOne().getPayload()))
          .copyHeaders(group.getOne().getHeaders())
          .build();
    }
    if (group.size() >= groupSizeThreshold) {
      return composeRecord(group);
    }

    Tuple2<Message<?>, Message<?>> prevAndLastMessages = findPrevAndLastMessages(group);
    Message<?> lastMessage = prevAndLastMessages.getT1();
    Message<?> second2LastMessage = prevAndLastMessages.getT2();
    assert (lastMessage != null) && (second2LastMessage != null);   // relying on check for singleton group
    // extract correlation headers in order to find out if the group is released by timeout or by completion
    Long lastMessageCorrId = lastMessage.getHeaders().get(CORRELATION_ID, Long.class);
    Long prevMessageCorrId = second2LastMessage.getHeaders().get(CORRELATION_ID, Long.class);

    boolean isGroupComplete = !Objects.equals(lastMessageCorrId, prevMessageCorrId);
    if (isGroupComplete) {
      group.remove(lastMessage);
      new MessagingTemplate(preAggregatorQueueChannel).send(lastMessage);
    }
    return composeRecord(group);
  }

  private Object composeRecord(MessageGroup group) {
    return MessageBuilder
        .withPayload(group.getMessages()
            .stream()
            .map(Message::getPayload)
            .map(Object::toString)
            .collect(toList()))
            //.collect(joining("\n")))
        .copyHeadersIfAbsent(group.getOne().getHeaders())   // in order not to loose 'logTimestamp' after release
        .build();
  }

  /**
   * @return last (T1) and second to last (T2) messages from given group; both messages can be {@code null} if the
   * group contains less than 2 or 1 elements
   */
  private Tuple2<Message<?>, Message<?>> findPrevAndLastMessages(MessageGroup group) {
    Message<?> lastMessage = null, second2LastMessage = null;
    for (Message<?> message : group.getMessages()) {
      second2LastMessage = lastMessage;
      lastMessage = message;
    }
    return Tuples.of(lastMessage, second2LastMessage);
  }

}
