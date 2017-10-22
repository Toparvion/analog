package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.RecordLevel;
import ru.ftc.upc.testing.analog.model.api.CompositeLinesPart;
import ru.ftc.upc.testing.analog.model.api.LinesPart;
import ru.ftc.upc.testing.analog.model.api.StyledLine;
import ru.ftc.upc.testing.analog.service.AnaLogUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;
import static ru.ftc.upc.testing.analog.service.AnaLogUtils.detectMessageType;

/**
 * @author Toparvion
 * @since v0.7
 */
@Component
public class RecordSender {
  private static final Logger log = LoggerFactory.getLogger(RecordSender.class);

  private final SimpMessagingTemplate messagingTemplate;

  @Autowired
  public RecordSender(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  void sendRecord(Message<?> recordMessage) {
    String uid = recordMessage.getHeaders().get(LOG_CONFIG_ENTRY_UID__HEADER, String.class);
    String sourceNode = recordMessage.getHeaders().get(SOURCE_NODE__HEADER, String.class);
    RecordLevel level = recordMessage.getHeaders().get(RECORD_LEVEL__HEADER, RecordLevel.class);
    LocalDateTime timestamp = recordMessage.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class);

    Object payload = recordMessage.getPayload();
    assert (payload instanceof Collection<?>);
    @SuppressWarnings("unchecked")
    List<String> payloadAsList = new ArrayList<>((Collection<String>) payload);
    // much like with log config entry, the absence of timestamp means that the payload is a list of plain records
    boolean isPlainRecords = (timestamp == null);
    List<StyledLine> styledLines = isPlainRecords
        ? preparePlainRecords(payloadAsList)
        : prepareCompositeRecords(payloadAsList, level);

    if (log.isTraceEnabled()) {
      log.trace("Рассылаемый фрагмент:\n{}", styledLines.stream()
          .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
          .collect(joining("\n")));
    }

    LinesPart linesPart;
    if (isPlainRecords) {
      linesPart = new LinesPart(styledLines);
    } else {
      long timestampMillis = timestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
      linesPart = new CompositeLinesPart(styledLines, sourceNode, timestampMillis);
    }
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + uid, linesPart);
  }

  /*private*/ List<StyledLine> prepareCompositeRecords(List<String> payloadAsList, RecordLevel firstLineLevel) {
    List<StyledLine> parsedLines = new ArrayList<>();
    if (payloadAsList.isEmpty()) {
      return parsedLines;
    }
    // самую первую строку записи обрабатываем отдельно, так как только она содержит метку уровня
    String firstLine = AnaLogUtils.distinguishXmlComposite(payloadAsList, 0);
    if (isXmlPrefixed(firstLine)) {
      throw new IllegalStateException(format("The very first line of the record is distinguished as XML but it " +
          "must contain timestamp only: '%s'", firstLine));
    }
    parsedLines.add(new StyledLine(AnaLogUtils.escapeSpecialCharacters(firstLine), firstLineLevel.name()));

    // остальные проверяем в цикле и проставляем им либо XML, либо PLAIN, так как других уровней быть не должно
    for (int i = 1; i < payloadAsList.size(); i++) {
      // check the line for the presence of XML
      String curLine = AnaLogUtils.distinguishXmlComposite(payloadAsList, i);
      // вставляем текст строки
      String text = AnaLogUtils.escapeSpecialCharacters(curLine);
      // определяем и вставляем уровень важности сообщения
      String style;
      if (isXmlPrefixed(text)) {
        style = "XML";
        text = stripXmlPrefix(text);
      } else {
        style = "PLAIN";
      }
      // завершаем оформление текущей строки
      parsedLines.add(new StyledLine(text, style));
    }
    return parsedLines;
  }

  /*private*/ List<StyledLine> preparePlainRecords(List<String> payloadAsList) {
    List<StyledLine> parsedLines = new ArrayList<>();
    for (int i = 0; i < payloadAsList.size(); i++) {
      // check the line for the presence of XML
      String curLine = AnaLogUtils.distinguishXml(payloadAsList, i);

      // вставляем текст строки
      String text = AnaLogUtils.escapeSpecialCharacters(curLine);
      // определяем и вставляем уровень важности сообщения
      String style = detectMessageType(curLine);

      // завершаем оформление текущей строки
      parsedLines.add(new StyledLine(text, style));
    }
    return parsedLines;
  }

  private boolean isXmlPrefixed(String text) {
    return text.startsWith("__XML__");
  }

  private String stripXmlPrefix(String text) {
    return text.substring("__XML__".length());
  }

}
