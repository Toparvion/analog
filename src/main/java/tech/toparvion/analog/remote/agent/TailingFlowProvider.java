package tech.toparvion.analog.remote.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.model.RecordLevel;
import tech.toparvion.analog.remote.agent.misc.CorrelationIdHeaderEnricher;
import tech.toparvion.analog.remote.agent.misc.SequenceNumberHeaderEnricher;
import tech.toparvion.analog.service.AnaLogUtils;
import tech.toparvion.analog.service.tail.TailSpecificsProvider;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.integration.dsl.channel.MessageChannels.queue;
import static org.springframework.integration.file.dsl.Files.tailAdapter;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class TailingFlowProvider {

  private final TimestampExtractor timestampExtractor;
  private final TailSpecificsProvider tailSpecificsProvider;
  private final int groupSizeThreshold;
  private final int groupTimeoutMs;


  @Autowired
  public TailingFlowProvider(TimestampExtractor timestampExtractor,
                             TailSpecificsProvider tailSpecificsProvider,
                             @Value("${tracking.groupSizeThreshold:50}") int groupSizeThreshold,
                             @Value("${tracking.groupTimeoutMs:1000}") int groupTimeoutMs) {
    this.timestampExtractor = timestampExtractor;
    this.tailSpecificsProvider = tailSpecificsProvider;
    this.groupSizeThreshold = groupSizeThreshold;
    this.groupTimeoutMs = groupTimeoutMs;
  }

  /**
   * The core method for building AnaLog dynamic behavior. Creates and returns an integration flow for watching given
   * log. No duplicate flow checking is done inside.
   * @param logPath full path to log file to tail
   * @return a new tailing flow
   */
  IntegrationFlow provideAggregatingFlow(String logPath) {
    // each tailing flow must have its own instance of correlationProvider as it is stateful and not thread-safe
    CorrelationIdHeaderEnricher correlationProvider = new CorrelationIdHeaderEnricher();
    // each tailing flow must have its own instance of sequenceProvider as it is stateful and not thread-safe
    SequenceNumberHeaderEnricher sequenceProvider = new SequenceNumberHeaderEnricher();

    /* When dealing with log message groups, some messages have to be resent to aggregator. Since the aggregator is
    capable of processing single message at a moment only, it is prepended with a queue channel that stores the incoming
    messages (including those to resend). This approach produces quite subtle situation - if the queue
    happens to be filled with some message(s) before another message returns there to be resent, the order of
    messages can be corrupted as the early arrived messages must leave the queue early as well (since the queue is of
    FIFO discipline). To avoid this, the queue is created as priority one. The priority is specified as simple
    sequence number (much like the one built in FileSplitter) and provided by dedicated header enricher. */
    //noinspection ConstantConditions     // null value for the header is prevented by message composing logic
    PriorityBlockingQueue<Message<?>> queue = new PriorityBlockingQueue<>(100,
        Comparator.comparingLong(message -> message.getHeaders().get(SEQUENCE_NUMBER__HEADER, Long.class)));
    MessageChannel preAggregatorQueueChannel = queue(queue).get();

    CompositeRecordAggregatorConfigurer recordAggregatorConfigurer
        = new CompositeRecordAggregatorConfigurer(preAggregatorQueueChannel, groupSizeThreshold, groupTimeoutMs);

    return IntegrationFlows
        .from(tailAdapter(new File(logPath))
            .id("tailSource:"+logPath)
            .nativeOptions(tailSpecificsProvider.getTailNativeOptions())
            .fileDelay(tailSpecificsProvider.getAttemptsDelay())
            .enableStatusReader(true))   // to receive events of log rotation, etc.
        .enrichHeaders(e -> e.headerFunction(LOG_TIMESTAMP_VALUE__HEADER, timestampExtractor::extractTimestamp))
        .enrichHeaders(e -> e.headerFunction(CORRELATION_ID, correlationProvider::obtainCorrelationId))
        .enrichHeaders(e -> e.headerFunction(RECORD_LEVEL__HEADER, this::detectRecordLevel))
        .enrichHeaders(e -> e.headerFunction(SEQUENCE_NUMBER__HEADER, sequenceProvider::assignSequenceNumber))
        .channel(preAggregatorQueueChannel)
        .aggregate(recordAggregatorConfigurer::configure)
        .channel(channels -> channels.publishSubscribe(logPath))
        .get();
  }

  IntegrationFlow providePlainFlow(String logPath) {
    return IntegrationFlows
        .from(tailAdapter(new File(logPath))
            .id("tailSource:"+logPath)
            .nativeOptions(tailSpecificsProvider.getTailNativeOptions())
            .fileDelay(tailSpecificsProvider.getAttemptsDelay())
            .enableStatusReader(true))   // to receive events log rotation, etc.
        .aggregate(aggregatorSpec -> aggregatorSpec
            .correlationStrategy(message -> BigDecimal.ONE)
            .releaseStrategy(group -> group.size() > groupSizeThreshold)
            .groupTimeout(groupTimeoutMs)
            .expireGroupsUponTimeout(true)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true))
        .channel(channels -> channels.publishSubscribe(logPath))
        .get();
  }

  private RecordLevel detectRecordLevel(Message<String> recordMessage) {
    if (!recordMessage.getHeaders().containsKey(LOG_TIMESTAMP_VALUE__HEADER)) {
      return null;
    }
    return AnaLogUtils.detectRecordLevel(recordMessage);
  }

}
