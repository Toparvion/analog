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
import tech.toparvion.analog.model.ServerFailure;
import tech.toparvion.analog.model.remote.AccessViolationTailingEvent;
import tech.toparvion.analog.service.origin.LogEventTypeDetector;
import tech.toparvion.analog.util.AnaLogUtils;

import static java.time.ZonedDateTime.now;
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
  private final LogEventTypeDetector dispatcher; 

  @Autowired
  public MetaDataSender(SimpMessagingTemplate messagingTemplate,
                        LogEventTypeDetector dispatcher) {
    this.messagingTemplate = messagingTemplate;
    this.dispatcher = dispatcher;
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

    // before going on, check if meta message is about access violation
    if (event instanceof AccessViolationTailingEvent) {
      String serverMessage = String.format("Access denied: path '%s' (or its referenced path) is not included " +
              "into 'allowed-log-locations' property.", logPath);
      ServerFailure failure = new ServerFailure(serverMessage, now());
      log.debug("Preparing tailing FAILURE for sending to clients.\nEvent: {}\nHeaders: node={}, destination='{}'",
              failure, sourceNode, destination);
      messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + destination, failure,
              singletonMap(MESSAGE_TYPE_HEADER, MessageType.FAILURE));
      return;
    }
    
    // if meta message is normal one, extract other properties
    String eventMessage = AnaLogUtils.extractMessage(event.toString());
    LogEventType detectedLogEventType = dispatcher.detectEventType(event);
    Metadata metadata = new Metadata(detectedLogEventType, logPath, sourceNode, eventMessage);
    log.debug("Preparing tailing event for sending to clients.\nEvent: {}\nHeaders: node={}, destination='{}'",
            event, sourceNode, destination);

    // and finally send it to all clients subscribed to this log
    messagingTemplate.convertAndSend(WEBSOCKET_TOPIC_PREFIX + destination,
            metadata, singletonMap(MESSAGE_TYPE_HEADER, MessageType.METADATA));
  
    
  }

}
