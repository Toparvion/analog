package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.RecordLevel;

import java.time.LocalDateTime;

import static ru.ftc.upc.testing.analog.remote.RemotingConstants.LOG_TIMESTAMP_VALUE__HEADER;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.RECORD_LEVEL__HEADER;

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
      log.info("Рассылается запись с меткой {}, уровнем {}, UID {}:\n< {}",
          recordMessage.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class),
          recordMessage.getHeaders().get(RECORD_LEVEL__HEADER, RecordLevel.class),
          recordMessage.getHeaders().get("uid", String.class),
          recordMessage.getPayload().toString().replaceAll("\\n", "\n< "));
//    }

//    messagingTemplate.convertAndSend(RemotingConstants.WEBSOCKET_TOPIC_PREFIX + "???", recordMessage);

  }
}
