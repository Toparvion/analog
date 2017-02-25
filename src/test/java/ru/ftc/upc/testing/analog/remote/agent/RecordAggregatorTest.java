package ru.ftc.upc.testing.analog.remote.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.aggregator.MessageGroupProcessor;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static ru.ftc.upc.testing.analog.remote.RemoteConfig.LOG_TIMESTAMP_HEADER;

class RecordAggregatorTest {
  private static final Logger log = LoggerFactory.getLogger(RecordAggregatorTest.class);

  /** Group release timeout should not be too long to keep tests fast but at the same time it can't be very short as
   * it may lead to interference with the speed of tests execution. One second is a good compromise.*/
  private static final int GROUP_TIMEOUT = 1000;

  private RecordAggregator sut;
  private MessageChannel outputChannelMock;

  @BeforeEach
  void prepareSutAndMocks() {
    MessageGroupProcessor processor = group -> MessageBuilder
        .withPayload(group.getMessages()
            .stream()
            .map(Message::getPayload)
            .map(Object::toString)
            .collect(joining("\n")))
        .copyHeadersIfAbsent(group.getOne().getHeaders())
        .build();
    sut = new RecordAggregator(processor, 3, GROUP_TIMEOUT);
    sut.setBeanName("RecordAggregator");      // to make SI log records not so long
    outputChannelMock = mock(MessageChannel.class);
    when(outputChannelMock.send(any())).thenAnswer(invocation -> {
      String record = invocation.<Message>getArgument(0).getPayload().toString();
      record = "> " + record.replaceAll("\\n", "\n> "); // to distinguish such lines from others in the log
      log.debug("OutputChannelMock received message:\n{}", record);
      return true;
    });
    sut.setOutputChannel(outputChannelMock);
    ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    taskScheduler.initialize();
    sut.setTaskScheduler(taskScheduler);
    //    sut.afterPropertiesSet();   // don't call as BeanFactory wasn't set up in this test's context
  }

  @Test
  @DisplayName("A group gets immediately released if its size reaches the threshold")
  void straightforwardGroupAccumulation() {
    String rawLines[] = {
        "02.10.14 09:12:49 WARN    SCT:148cea18683 /personal/ pdkem lite.web.customer.sclub.util.SClubUtils - Calls promo action for account id: 2171336801",
            "java.lang.RuntimeException:",
            "\tat lite.web.customer.sclub.util.SClubUtils.getAgentId(SClubUtils.java:670)",
    };

    Arrays.stream(rawLines)
        .map(payload -> buildMessage(payload, 111111111111L))
        .forEachOrdered(sut::handleMessage);

    String expectedRecord = Arrays.stream(rawLines)
                                  .collect(joining("\n"));

    verify(outputChannelMock).send(argThat(matchesMessageWith(expectedRecord)));
  }

  @Test
  @DisplayName("A group is released if its size has not reached the threshold within timeout")
  void releasingByTimeout() {
    String rawLines[] = {
        "02.10.14 09:12:49 WARN    SCT:148cea18683 /personal/ pdkem lite.web.customer.sclub.util.SClubUtils - Calls promo action for account id: 2171336801",
        "java.lang.RuntimeException:",
    };

    Arrays.stream(rawLines)
        .map(payload -> buildMessage(payload, 111111111111L))
        .forEachOrdered(sut::handleMessage);

    String expectedRecord = Arrays.stream(rawLines)
                                  .collect(joining("\n"));

    // the aggregator shouldn't release the record immediately because it can be appended in a few moments
    verify(outputChannelMock, never()).send(argThat(matchesMessageWith(expectedRecord)));
    // so that we must wait this period to make sure the record was eventually released
    verify(outputChannelMock, timeout(GROUP_TIMEOUT+500 /*to mitigate time measure inaccuracy*/))
        .send(argThat(matchesMessageWith(expectedRecord)));
  }

  @Test
  @DisplayName("A group is released immediately if the next one has another correlationId")
  void previousGroupRelease() {
    String rawLines[] = {
        "02.10.14 09:12:49 WARN    SCT:148cea18683 /personal/ pdkem lite.web.customer.sclub.util.SClubUtils - Calls promo action for account id: 2171336801",
        "java.lang.RuntimeException:",
        "02.10.14 09:12:50 DEBUG   SCT:148cea18683 /personal/ pdkem bankplus.SessionObject - loadUserProperties for 2171336768 - loaded..."
    };

    // convert raw lines into messages and equip them with correlationId header
    Stream<Message<String>> firstRecordLines = Stream.of(0, 1)
        .map(index -> rawLines[index])
        .map(payload -> buildMessage(payload, 111111111111L));
    Stream<Message<String>> secondRecordLines = Stream.of(2)
        .map(index -> rawLines[index])
        .map(payload -> buildMessage(payload, 222222222222L));
    Message[] lineMessages = Stream.concat(firstRecordLines, secondRecordLines)
                                   .toArray(Message[]::new);

    // prepare the records to wait for as the output of aggregator
    String firstExpectedRecord = Stream.of(0,1)
                                       .map(i -> rawLines[i])
                                       .collect(joining("\n"));
    String secondExpectedRecord = rawLines[2];


    log.info("=============================== Message #1 ===============================");
    sut.handleMessage(lineMessages[0]);
    verify(outputChannelMock, never()).send(any(Message.class));

    log.info("=============================== Message #2 ===============================");
    sut.handleMessage(lineMessages[1]);
    verify(outputChannelMock, never()).send(any(Message.class));

    log.info("=============================== Message #3 ===============================");
    sut.handleMessage(lineMessages[2]);
    // previous group must be released as the new one has arrived
    verify(outputChannelMock).send(argThat(matchesMessageWith(firstExpectedRecord)));
    // but it is not the right time to release the new group as it may be updated later
    verify(outputChannelMock, never()).send(argThat(matchesMessageWith(secondExpectedRecord)));
    // nevertheless the new group should not wait forever to be updated, it must be released in timeout
    verify(outputChannelMock, timeout(GROUP_TIMEOUT + 500))
        .send(argThat(matchesMessageWith(secondExpectedRecord)));
  }

  @Test
  @DisplayName("An empty group pointed by previousGroupId is skipped correctly")
  void emptyGroupSkipping() {
    String rawLines[] = {
        "02.10.14 09:12:49 WARN    SCT:148cea18683 /personal/ pdkem lite.web.customer.sclub.util.SClubUtils - Calls promo action for account id: 2171336801",
        "java.lang.RuntimeException:",
        "\tat web.customer.sclub.util.SClubUtils.getAgentId(SClubUtils.java:670)",
        "\tat client.zkdp.ZKDPManager.loadCardInfo(ZKDPManager.java:207)",
    };

    Stream<Message<String>> firstStream = Stream.of(0, 1, 2)
                                                .map(i -> rawLines[i])
                                                .map(payload -> buildMessage(payload, 111111111111L));
    Stream<Message<String>> secondStream = Stream.of(3)
                                                 .map(i -> rawLines[i])
                                                 .map(payload -> buildMessage(payload, 222222222222L));
    Message[] lineMessages = Stream.concat(firstStream, secondStream)
                                   .toArray(Message[]::new);

    // prepare the records to wait for as the output of aggregator
    String firstExpectedRecord = Stream.of(0, 1, 2)
                                       .map(i -> rawLines[i])
                                       .collect(joining("\n"));
    String secondExpectedRecord = rawLines[3];


    log.info("=============================== Message #1 ===============================");
    sut.handleMessage(lineMessages[0]);
    verify(outputChannelMock, never()).send(any(Message.class));

    log.info("=============================== Message #2 ===============================");
    sut.handleMessage(lineMessages[1]);
    verify(outputChannelMock, never()).send(any(Message.class));

    log.info("=============================== Message #3 ===============================");
    sut.handleMessage(lineMessages[2]);
    verify(outputChannelMock).send(argThat(matchesMessageWith(firstExpectedRecord)));

    log.info("=============================== Message #4 ===============================");
    sut.handleMessage(lineMessages[3]);
    // it is not the right time to release the new group as it may be updated later
    verify(outputChannelMock, never()).send(argThat(matchesMessageWith(secondExpectedRecord)));
    // but the new group should not wait forever to be updated, it must be released in timeout
    verify(outputChannelMock, timeout(GROUP_TIMEOUT + 500))
        .send(argThat(matchesMessageWith(secondExpectedRecord)));
    // there is also a log message should be printed: 'Previous group with id=111111111111 has been already released. Skip.'
  }

  @Test
  @DisplayName("Timestamp header is included into released group independently of the completion way")
  void timestampHeadersAreTransferredCorrectly() {
    String rawLines[] = {
        "02.10.14 09:12:49 WARN    SCT:148cea18683 /personal/ pdkem lite.web.customer.sclub.util.SClubUtils - Calls promo action for account id: 2171336801",
        "java.lang.RuntimeException:",
        "\tat lite.web.customer.sclub.util.SClubUtils.getAgentId(SClubUtils.java:670)",
    };
    LocalDateTime recordTimestamp = LocalDateTime.now();

    List<Message<String>> lineMessages = new ArrayList<>();
    for (int i = 0; i < rawLines.length; i++) {
      String rawLine = rawLines[i];
      MessageBuilder<String> messageBuilder = MessageBuilder
          .withPayload(rawLine)
          .setCorrelationId(111111111111L);
      if (i == 0) {
        messageBuilder.setHeader(LOG_TIMESTAMP_HEADER, recordTimestamp);
      }
      lineMessages.add(messageBuilder.build());
    }

    lineMessages.forEach(sut::handleMessage);

    verify(outputChannelMock, only()).send(argThat(hasHeader(LOG_TIMESTAMP_HEADER, recordTimestamp)));
  }

  private Message<String> buildMessage(String payload, long correlationId) {
    return MessageBuilder
        .withPayload(payload)
        .setHeader(CORRELATION_ID, correlationId)
        .build();
  }

  private ArgumentMatcher<Message<?>> matchesMessageWith(String expectedRecord) {
    return message -> expectedRecord.equals(message.getPayload().toString());
  }

  private ArgumentMatcher<Message<?>> hasHeader(String headerName, Object headerValue) {
    return message -> headerValue.equals(message.getHeaders().get(headerName));
  }
}