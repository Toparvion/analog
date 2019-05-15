package tech.toparvion.analog.service.origin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.util.AnaLogUtils;

import java.util.Map;
import java.util.Optional;

/**
 * Entry point to log event type detection logic
 * 
 * @author Toparvion
 * @since v0.12
 */
@Service
public class LogEventTypeDetector {

  private final Map<String, LogEventTypeRecognizer> detectorsMap;

  @Autowired
  public LogEventTypeDetector(Map<String, LogEventTypeRecognizer> detectorsMap) {
    this.detectorsMap = detectorsMap;
  }

  public LogEventType detectEventType(FileTailingEvent event) {
    String logPath = event.getFile().getAbsolutePath();
    String eventMessage = AnaLogUtils.extractMessage(event.toString());
    // map tail's message text to AnaLog's event type
    LogEventType detectedLogEventType = null;
    for (LogEventTypeRecognizer detector : detectorsMap.values()) {
      Optional<LogEventType> detectedOpt = detector.detectEventType(eventMessage, logPath);
      if (detectedOpt.isPresent()) {
        detectedLogEventType = detectedOpt.get();
        break;
      }
    }
    Assert.notNull(detectedLogEventType, "Unable to find appropriate detector for log event: " + event.toString());
    return detectedLogEventType;
  }
}
