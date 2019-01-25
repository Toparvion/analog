package tech.toparvion.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.Metadata;
import tech.toparvion.analog.remote.server.origin.detect.LogEventTypeDetector;
import tech.toparvion.analog.util.AnaLogUtils;

import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonMap;
import static tech.toparvion.analog.remote.RemotingConstants.*;

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
  private final Map<String, LogEventTypeDetector> detectorsMap;

  @Autowired
  public MetaDataSender(SimpMessagingTemplate messagingTemplate,
                        Map<String, LogEventTypeDetector> detectorsMap) {
    this.messagingTemplate = messagingTemplate;
    this.detectorsMap = detectorsMap;
  }

  void sendMetaData(Message<?> metaMessage) {
    // extract header values in order to include them into metadata being sent
    String destination = metaMessage.getHeaders().get(CLIENT_DESTINATION__HEADER, String.class);
    String sourceNode = metaMessage.getHeaders().get(SOURCE_NODE__HEADER, String.class);

    // extract payload - the tailing event itself
    Object payload = metaMessage.getPayload();
    Assert.isInstanceOf(FileTailingEvent.class, payload);
    FileTailingEvent event = (FileTailingEvent) payload;
    String logPath = event.getFile().getAbsolutePath();
    String eventMessage = AnaLogUtils.extractMessage(event.toString());
    // map tail's message text to AnaLog's event type
    LogEventType detectedLogEventType = null;
    for (LogEventTypeDetector detector : detectorsMap.values()) {
      Optional<LogEventType> detectedOpt = detector.detectEventType(eventMessage, logPath);
      if (detectedOpt.isPresent()) {
        detectedLogEventType = detectedOpt.get();
        break;
      }
    }
    Assert.notNull(detectedLogEventType, "Unable to find appropriate detector for log event: " + event.toString());

    Metadata metadata = new Metadata(detectedLogEventType, logPath, sourceNode, eventMessage);
    log.debug("Preparing tailing event for sending to clients.\nEvent: {}\nHeaders: node={}, destination='{}'",
        event, sourceNode, destination);

    // and finally send it to all clients subscribed to this log
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + destination,
        metadata, singletonMap(MESSAGE_TYPE_HEADER, MessageType.METADATA));
  }

}
