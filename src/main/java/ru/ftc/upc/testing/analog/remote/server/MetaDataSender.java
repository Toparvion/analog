package ru.ftc.upc.testing.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.model.Metadata;
import ru.ftc.upc.testing.analog.model.TailEventType;
import ru.ftc.upc.testing.analog.service.tail.TailSpecificsProvider;
import ru.ftc.upc.testing.analog.service.tail.UnrecognizedTailEventException;

import static java.util.Collections.singletonMap;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*;

/**
 * A service for sending tailing metadata to clients through websocket. With its help clients get notified about log
 * rotation, deletion, etc.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class MetaDataSender {
  private static final Logger log = LoggerFactory.getLogger(MetaDataSender.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final TailSpecificsProvider tailSpecificsProvider;

  @Autowired
  public MetaDataSender(SimpMessagingTemplate messagingTemplate, TailSpecificsProvider tailSpecificsProvider) {
    this.messagingTemplate = messagingTemplate;
    this.tailSpecificsProvider = tailSpecificsProvider;
  }

  public void sendMetaData(Message<?> metaMessage) {
    // extract header values in order to include them into metadata being sent
    String uid = metaMessage.getHeaders().get(LOG_CONFIG_ENTRY_UID__HEADER, String.class);
    String sourceNode = metaMessage.getHeaders().get(SOURCE_NODE__HEADER, String.class);

    // extract payload - the tailing event itself
    Object payload = metaMessage.getPayload();
    assert (payload instanceof FileTailingEvent);
    FileTailingEvent event = (FileTailingEvent) payload;
    // map tail's message text too AnaLog's event type
    TailEventType eventType;
    try {
      eventType = tailSpecificsProvider.detectEventType(event.toString());

    } catch (UnrecognizedTailEventException e) {
      log.error("Server received an event from tail process (uid='{}' on node='{}') but couldn't send it to clients " +
          "within metadata because failed to detect the event's type with {}. Text of the event: \n{}",
          uid, sourceNode, tailSpecificsProvider.getClass().getSimpleName(), e.getMessage());
      return;
    }
    String logPath = event.getFile().getAbsolutePath();
    Metadata metadata = new Metadata(eventType, logPath, sourceNode);
    log.debug("Preparing tailing event for sending to clients.\nEvent: {}\nHeaders: node={}, uid='{}'", event, sourceNode, uid);

    // and finally send it to all clients subscribed to this log
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + uid,
        metadata, singletonMap(MESSAGE_TYPE_HEADER, MessageType.METADATA));
  }

}
