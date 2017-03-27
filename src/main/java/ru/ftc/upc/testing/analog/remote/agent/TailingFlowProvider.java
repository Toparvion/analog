package ru.ftc.upc.testing.analog.remote.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.RecordLevel;
import ru.ftc.upc.testing.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.util.stream.Stream;

import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.integration.file.dsl.Files.tailAdapter;
import static ru.ftc.upc.testing.analog.model.RecordLevel.UNKNOWN;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class TailingFlowProvider {

  private final  TimestampExtractor timestampExtractor;

  @Autowired
  public TailingFlowProvider(TimestampExtractor timestampExtractor) {
    this.timestampExtractor = timestampExtractor;
  }

  /**
   * TODO DOCME
   * @param logPath
   * @return
   */
  IntegrationFlow provideTailingFlow(String logPath) {
    // each tailing flow must have its own instance of correlationProvider as it is stateful and not thread-safe
    CorrelationIdHeaderEnricher correlationProvider = new CorrelationIdHeaderEnricher();

    int groupSizeThreshold = 3;         // TODO move to properties
    int groupTimeout = 10000;           // TODO move to properties

    MessageChannel preAggregatorQueueChannel = MessageChannels.queue(RECORD_AGGREGATOR_INPUT_CHANNEL).get();
    RecordAggregatorConfigurer recordAggregatorConfigurer
        = new RecordAggregatorConfigurer(preAggregatorQueueChannel, groupSizeThreshold, groupTimeout);

    return IntegrationFlows
        .from(tailAdapter((new File(logPath)))
            .id("tailSource"))
        .enrichHeaders(e -> e.headerFunction(LOG_TIMESTAMP_VALUE__HEADER, timestampExtractor::extractTimestamp))
        .enrichHeaders(e -> e.headerFunction(CORRELATION_ID, correlationProvider::obtainCorrelationId))
        .channel(preAggregatorQueueChannel)
        .aggregate(recordAggregatorConfigurer::configure)
//        .handle(new RecordAggregatorV1(this::composeRecord, groupSizeThreshold, groupTimeout))
        .enrichHeaders(e -> e.headerFunction(RECORD_LEVEL__HEADER, this::detectRecordLevel))
        .channel(channels -> channels.publishSubscribe(logPath))
        .get();
  }

  private RecordLevel detectRecordLevel(Message<String> recordMessage) {
    String record = recordMessage.getPayload();
    int eolIndex = record.indexOf('\n');
    int searchBoundary = (eolIndex != -1) ? eolIndex : record.length();
    String recordFirstLine = record.substring(0, searchBoundary);
    return Stream.of(RecordLevel.values())
        .filter(level -> !UNKNOWN.equals(level))
        .filter(level -> recordFirstLine.contains(level.name()))
        .findAny()
        .orElse(UNKNOWN);
  }

}
