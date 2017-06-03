package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.RecordLevel;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

/**
 * @author Toparvion
 * @since v0.7
 */
@Component
public class WebSocketRecordSender {
  private static final Logger log = LoggerFactory.getLogger(WebSocketRecordSender.class);

  private final SimpMessagingTemplate messagingTemplate;

  @Autowired
  public WebSocketRecordSender(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  void sendRecord(Message<?> recordMessage) {
//    if (log.isTraceEnabled()) {
//      log.info("Sending record with timestamp {} and level {}:\n< {}",
    String uid = recordMessage.getHeaders().get(LOG_CONFIG_ENTRY_UID__HEADER, String.class);
    RecordLevel level = recordMessage.getHeaders().get(RECORD_LEVEL__HEADER, RecordLevel.class);
    LocalDateTime localDateTime = recordMessage.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class);

    Object payload = recordMessage.getPayload();
    String record;
    if (payload instanceof List<?>) {
      @SuppressWarnings("unchecked")
      List<String> payloadAsList = (List<String>) payload;
      record = payloadAsList.stream().collect(joining("\n< ", "\n< ", ""));
    } else {
      String payloadAsString = (String) payload;
      record = "\n< " + payloadAsString;
    }
    log.info("Рассылается запись с меткой {}, уровнем {}, UID {}:{}", localDateTime, level, uid, record);

//    }
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + uid, record);
  }
}
