package ru.ftc.upc.testing.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.aggregator.AggregatingMessageHandler;
import org.springframework.integration.aggregator.MessageGroupProcessor;
import org.springframework.integration.expression.ValueExpression;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.messaging.Message;

import static java.lang.String.format;
import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;

/**
 * A Spring Integration aggregator that implements 'look-back' strategy for releasing groups of messages.
 * <p>Created by Toparvion on 21.02.2017.
 */
public class RecordAggregator extends AggregatingMessageHandler {
  private static final Logger log = LoggerFactory.getLogger(RecordAggregator.class);

  private Long previousGroupId = null;

  public RecordAggregator(MessageGroupProcessor processor, int groupSizeInclusiveThreshold, long groupTimeout) {
    super(processor);
    super.setMessageStore(new SimpleMessageStore());
    super.setReleaseStrategy(group -> group.size() >= groupSizeInclusiveThreshold);
    super.setGroupTimeoutExpression(new ValueExpression<>(groupTimeout));
    super.setExpireGroupsUponCompletion(true);
    super.setExpireGroupsUponTimeout(true);
    super.setSendPartialResultOnExpiry(true);
    super.setBeanName(RecordAggregator.class.getSimpleName());
  }

  @Override
  protected void handleMessageInternal(Message<?> message) throws Exception {
    Long currentGroupId = message.getHeaders().get(CORRELATION_ID, Long.class);
    assert (currentGroupId != null) : format("Message %s has no CORRELATION_ID header. " +
        "Check CorrelationIdHeaderEnricher", message);

    boolean needToReleasePreviousGroup = ((previousGroupId != null) && !currentGroupId.equals(previousGroupId));
    if (needToReleasePreviousGroup) {
      MessageGroup previousGroup = getMessageStore().getMessageGroup(previousGroupId);
      if (previousGroup.size() != 0) {
        log.debug("Previous group (id={}) is about to be released as the new message has another correlationId={}.",
            previousGroupId, currentGroupId);
        forceComplete(previousGroup);
        log.debug("Previous group (id={}) has been released. Proceeding to handling current message...", previousGroupId);
      } else {
        log.info("Previous group with id={} has been already released. Skip.", previousGroupId);
      }
    }

    super.handleMessageInternal(message);
    previousGroupId = currentGroupId;   // do unconditionally as we'll check that group on the next step anyway
  }
}
