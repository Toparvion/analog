package tech.toparvion.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.api.CompositeLinesPart;
import tech.toparvion.analog.model.api.LinesPart;
import tech.toparvion.analog.model.api.StyledLine;
import tech.toparvion.analog.service.RecordLevelDetector;
import tech.toparvion.analog.util.AnaLogUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.springframework.integration.file.FileHeaders.ORIGINAL_FILE;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 * A service for sending main payload - log records - to clients.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class RecordSender {
  private static final Logger log = LoggerFactory.getLogger(RecordSender.class);

  private final RecordLevelDetector recordLevelDetector; 
  private final SimpMessagingTemplate messagingTemplate;
  private final ColorPicker colorPicker;

  @Autowired
  public RecordSender(RecordLevelDetector recordLevelDetector, 
                      SimpMessagingTemplate messagingTemplate, 
                      ColorPicker colorPicker) {
    this.recordLevelDetector = recordLevelDetector;
    this.messagingTemplate = messagingTemplate;
    this.colorPicker = colorPicker;
  }

  void sendRecord(Message<?> recordMessage) {
    String destination = recordMessage.getHeaders().get(CLIENT_DESTINATION__HEADER, String.class);
    String sourceNode = recordMessage.getHeaders().get(SOURCE_NODE__HEADER, String.class);
    String sourcePath = requireNonNull(recordMessage.getHeaders().get(ORIGINAL_FILE, File.class)).getAbsolutePath();
    String level = recordMessage.getHeaders().get(RECORD_LEVEL__HEADER, String.class);
    LocalDateTime timestamp = recordMessage.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class);

    Object payload = recordMessage.getPayload();
    Assert.isInstanceOf(Collection.class, payload );
    @SuppressWarnings("unchecked")
    List<String> payloadAsList = new ArrayList<>((Collection<String>) payload);
    // much like with log config entry, the absence of timestamp means that the payload is a flat list of records
    boolean isFlatMessage = (timestamp == null);
    List<StyledLine> styledLines = isFlatMessage
        ? prepareFlatMessage(payloadAsList)
        : prepareGroupMessage(payloadAsList, level);

    if (log.isTraceEnabled()) {
      log.trace("Fragment being sent:\n{}", styledLines.stream()
          .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
          .collect(joining("\n")));
    }

    LinesPart linesPart;
    if (isFlatMessage) {
      linesPart = new LinesPart(styledLines);
    } else {
      long timestampMillis = timestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
      String highlightColor = colorPicker.pickColor(sourcePath, sourceNode, destination);
      linesPart = new CompositeLinesPart(styledLines, sourceNode, sourcePath, timestampMillis, highlightColor);
    }
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + destination,
        linesPart, singletonMap(MESSAGE_TYPE_HEADER, MessageType.RECORD));
  }

  /*private*/ List<StyledLine> prepareGroupMessage(List<String> payloadAsList, String firstLineLevel) {
    List<StyledLine> parsedLines = new ArrayList<>();
    if (payloadAsList.isEmpty()) {
      return parsedLines;
    }
    // самую первую строку записи обрабатываем отдельно, так как только она содержит метку уровня
    String firstLine = AnaLogUtils.distinguishXml4Group(payloadAsList, 0);
    if (isXmlPrefixed(firstLine)) {
      throw new IllegalStateException(format("The very first line of the record is distinguished as XML but it " +
          "must contain timestamp only: '%s'", firstLine));
    }
    parsedLines.add(new StyledLine(AnaLogUtils.escapeSpecialCharacters(firstLine), firstLineLevel));

    // остальные проверяем в цикле и проставляем им либо XML, либо PLAIN, так как других уровней быть не должно
    for (int i = 1; i < payloadAsList.size(); i++) {
      // check the line for the presence of XML
      String curLine = AnaLogUtils.distinguishXml4Group(payloadAsList, i);
      // вставляем текст строки
      String text = AnaLogUtils.escapeSpecialCharacters(curLine);
      // определяем и вставляем уровень важности сообщения
      String style;
      if (isXmlPrefixed(text)) {
        style = "XML";
        text = stripXmlPrefix(text);
      } else {
        style = PLAIN_RECORD_LEVEL_NAME;
      }
      // завершаем оформление текущей строки
      parsedLines.add(new StyledLine(text, style));
    }
    return parsedLines;
  }

  /*private*/ List<StyledLine> prepareFlatMessage(List<String> payloadAsList) {
    List<StyledLine> parsedLines = new ArrayList<>();
    for (int i = 0; i < payloadAsList.size(); i++) {
      // check the line for the presence of XML
      String curLine = AnaLogUtils.distinguishXml(payloadAsList, i);

      // insert the text of the line 
      String text = AnaLogUtils.escapeSpecialCharacters(curLine);
      // detect and set the importance level of the line (this also may appear an XML line) 
      String style = recordLevelDetector.detectLevel(curLine)
                                        .orElseGet(() -> AnaLogUtils.checkIfXml(curLine));
      // finish current line construction
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
