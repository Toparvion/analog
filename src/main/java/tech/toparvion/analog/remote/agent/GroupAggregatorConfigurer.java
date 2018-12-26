package tech.toparvion.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.AggregatorSpec;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import javax.annotation.Nullable;
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
class GroupAggregatorConfigurer {
  private static final Logger log = LoggerFactory.getLogger(GroupAggregatorConfigurer.class);

  private final MessagingTemplate preAggregatorQueueSender;
  private final int groupSizeThreshold;
  private final long groupTimeout;

  GroupAggregatorConfigurer(MessageChannel preAggregatorQueueChannel, int groupSizeThreshold, long groupTimeout) {
    this.preAggregatorQueueSender = new MessagingTemplate(preAggregatorQueueChannel);
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
        .poller(p -> p.fixedDelay(50, MILLISECONDS));
  }

  private boolean releaseStrategy(MessageGroup group) {
    assert group.size() > 0;
    if (group.size() == 1) {
      return false; // i.e. if group consists of just one element and thus is insufficient for making decision
    }
    if (group.size() >= groupSizeThreshold) {
      return true;
    }
    MessageTwain prevAndLastMessages = findPrevAndLastMessages(group);
    Message<?> lastMessage = prevAndLastMessages.getM1();
    Message<?> second2LastMessage = prevAndLastMessages.getM2();
    assert (lastMessage != null) && (second2LastMessage != null);   // relying on check for singleton group
    Long lastMessageCorrId = lastMessage.getHeaders().get(CORRELATION_ID, Long.class);
    Long prevMessageCorrId = second2LastMessage.getHeaders().get(CORRELATION_ID, Long.class);

    boolean isGroupComplete = !Objects.equals(lastMessageCorrId, prevMessageCorrId);
    if (isGroupComplete) {
      log.trace("Group {} is about to be released as last corrId {} differs from previous ones {}.",
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

    MessageTwain prevAndLastMessages = findPrevAndLastMessages(group);
    Message<?> lastMessage = prevAndLastMessages.getM1();
    Message<?> second2LastMessage = prevAndLastMessages.getM2();
    assert (lastMessage != null) && (second2LastMessage != null);   // relying on check for singleton group
    // extract correlation headers in order to find out if the group is released by timeout or by completion
    Long lastMessageCorrId = lastMessage.getHeaders().get(CORRELATION_ID, Long.class);
    Long prevMessageCorrId = second2LastMessage.getHeaders().get(CORRELATION_ID, Long.class);

    boolean isGroupComplete = !Objects.equals(lastMessageCorrId, prevMessageCorrId);
    if (isGroupComplete) {
      group.remove(lastMessage);
      preAggregatorQueueSender.send(lastMessage);
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
   * @return last (M1) and second to last (M2) messages from given group; both messages can be {@code null} if the
   * group contains less than 2 or 1 elements
   */
  private MessageTwain findPrevAndLastMessages(MessageGroup group) {
    Message<?> lastMessage = null, second2LastMessage = null;
    for (Message<?> message : group.getMessages()) {
      second2LastMessage = lastMessage;
      lastMessage = message;
    }
    return new MessageTwain(lastMessage, second2LastMessage);
  }

  private static class MessageTwain {
    @Nullable
    private final Message<?> m1;
    @Nullable
    private final Message<?> m2;

    private MessageTwain(@Nullable Message<?> m1, @Nullable Message<?> m2) {
      this.m1 = m1;
      this.m2 = m2;
    }

    @Nullable
    Message<?> getM1() {
      return m1;
    }

    @Nullable
    Message<?> getM2() {
      return m2;
    }
  }

}
