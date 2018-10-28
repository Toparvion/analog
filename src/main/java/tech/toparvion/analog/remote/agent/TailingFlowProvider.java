package tech.toparvion.analog.remote.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext.IntegrationFlowRegistration;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.remote.agent.misc.CorrelationIdHeaderEnricher;
import tech.toparvion.analog.remote.agent.misc.SequenceNumberHeaderEnricher;
import tech.toparvion.analog.service.RecordLevelDetector;
import tech.toparvion.analog.service.tail.TailSpecificsProvider;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.integration.dsl.MessageChannels.publishSubscribe;
import static org.springframework.integration.dsl.MessageChannels.queue;
import static org.springframework.integration.file.dsl.Files.tailAdapter;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class TailingFlowProvider {
  private static final Logger log = LoggerFactory.getLogger(TailingFlowProvider.class);

  private static final String TAIL_FLOW_PREFIX = "tailFlow_";
  private static final String TAIL_OUTPUT_CHANNEL_PREFIX = "tailOutputChannel_";

  private final TimestampExtractor timestampExtractor;
  private final TailSpecificsProvider tailSpecificsProvider;
  private final RecordLevelDetector recordLevelDetector;
  private final IntegrationFlowContext flowContext;
  private final int groupSizeThreshold;
  private final int groupTimeoutMs;


  @Autowired
  public TailingFlowProvider(TimestampExtractor timestampExtractor,
                             TailSpecificsProvider tailSpecificsProvider,
                             RecordLevelDetector recordLevelDetector,
                             IntegrationFlowContext flowContext,
                             @Value("${tracking.group.sizeThreshold:500}") int groupSizeThreshold,
                             @Value("${tracking.group.timeoutMs:500}") int groupTimeoutMs) {
    this.timestampExtractor = timestampExtractor;
    this.tailSpecificsProvider = tailSpecificsProvider;
    this.recordLevelDetector = recordLevelDetector;
    this.flowContext = flowContext;
    this.groupSizeThreshold = groupSizeThreshold;
    this.groupTimeoutMs = groupTimeoutMs;
  }

  /**
   * The core method for building AnaLog dynamic behavior (for grouping logs).
   * Creates and returns a grouping integration flow for watching given log. No duplicate flow checking is done inside.
   * @param logPath full path to log file to tail
   * @param isTailNeeded should 'tail' include last several lines of the log?
   * @return a new tailing flow
   */
  IntegrationFlow provideGroupingFlow(String logPath, boolean isTailNeeded) {
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

    GroupingAggregatorConfigurer recordAggregatorConfigurer
        = new GroupingAggregatorConfigurer(preAggregatorQueueChannel, groupSizeThreshold, groupTimeoutMs);

    return IntegrationFlows
        .from(findOrCreateTailFlow(logPath, false, isTailNeeded))
        .enrichHeaders(e -> e.headerFunction(LOG_TIMESTAMP_VALUE__HEADER, timestampExtractor::extractTimestamp))
        .enrichHeaders(e -> e.headerFunction(CORRELATION_ID, correlationProvider::obtainCorrelationId))
        .enrichHeaders(e -> e.headerFunction(RECORD_LEVEL__HEADER, this::detectRecordLevel))
        .enrichHeaders(e -> e.headerFunction(SEQUENCE_NUMBER__HEADER, sequenceProvider::assignSequenceNumber))
        .channel(preAggregatorQueueChannel)
        .aggregate(recordAggregatorConfigurer::configure)
        .channel(publishSubscribe())
        .get();
  }

  /**
   * The core method for building AnaLog dynamic behavior (for flat logs).
   * Creates and returns a flat integration flow for watching given log. No duplicate flow checking is done inside.
   * @param logPath full path to log file to tail
   * @param isTailNeeded should 'tail' include last several lines of the log?
   * @return a new tailing flow
   */
  IntegrationFlow provideFlatFlow(String logPath, boolean isTailNeeded) {
    return IntegrationFlows
        .from(findOrCreateTailFlow(logPath, true, isTailNeeded))
        .aggregate(aggregatorSpec -> aggregatorSpec
            .correlationStrategy(message -> BigDecimal.ONE)
            .releaseStrategy(group -> group.size() > groupSizeThreshold)
            .groupTimeout(groupTimeoutMs)
            .expireGroupsUponTimeout(true)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true))
        .channel(publishSubscribe())
        .get();
  }

  /**
   * Checks whether tail flow for given log already exists and, if not, creates it. Either way composes name of the
   * flow's output pub-sub channel and returns it as result.
   * @param logPath absolute path to log file to tail
   * @param isLogPlain is log to watch is flat (not grouping)
   * @param isTailNeeded whether previous lines of log file are required or not
   * @return bean name of the flow's output channel to subscribe to
   * @implNote the method is declared {@code synchronized} in order to prevent double flow registration in case of
   * simultaneous requests from clients
   */
  private synchronized String findOrCreateTailFlow(String logPath, boolean isLogPlain, boolean isTailNeeded) {
    // tail output channel name does not depend on whether the flow already exists or not, so we can define it early
    String tailOutputChannelName = TAIL_OUTPUT_CHANNEL_PREFIX + logPath;
    // to prevent double tailing let's check if such tail flow already exists
    IntegrationFlowRegistration tailFlowRegistration = flowContext.getRegistrationById(TAIL_FLOW_PREFIX + logPath);
    if (tailFlowRegistration == null) {
      log.debug("No tail flow found for log '{}'. Will create a new one...", logPath);
      // first declare the tail flow alongside with its output channel (to subscribe to some time later)
      String tailNativeOptions = isLogPlain
          ? tailSpecificsProvider.getPlainTailNativeOptions(isTailNeeded)
          : tailSpecificsProvider.getCompositeTailNativeOptions(isTailNeeded);
      StandardIntegrationFlow tailFlow = IntegrationFlows
          .from(tailAdapter(new File(logPath))
              .id("tailProcess_" + logPath)
              .nativeOptions(tailNativeOptions)
              .fileDelay(tailSpecificsProvider.getAttemptsDelay())
              .enableStatusReader(true))   // to receive events of log rotation, etc.
          .channel(publishSubscribe(tailOutputChannelName))
          .get();
      // then register it within the app context
      tailFlowRegistration = flowContext.registration(tailFlow)
          .id(TAIL_FLOW_PREFIX + logPath)
          .autoStartup(true)      // may be postpone this to the moment when the client gets ready to receive messages
          .register();
      log.info("New tail flow has been registered with id='{}'.", tailFlowRegistration.getId());

    } else {
      // we suppose that tail flow is structurally immutable so that the existence of a flow guarantees the existence
      // of its output channel with corresponding name
      log.debug("Found existing tail flow with output channel '{}'. Will reuse it.", tailOutputChannelName);

    }

    return tailOutputChannelName;
  }

  @Nullable
  private String detectRecordLevel(Message<String> recordMessage) {
    if (!recordMessage.getHeaders().containsKey(LOG_TIMESTAMP_VALUE__HEADER)) {
      return null;
    }
    return recordLevelDetector.detectLevel(recordMessage.getPayload())
                              .orElse(PLAIN_RECORD_LEVEL_NAME);
  }

}
