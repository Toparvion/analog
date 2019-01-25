package tech.toparvion.analog.remote.server.origin.detect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.config.entry.LogType;

import java.util.Optional;
import java.util.Set;

/**
 * @author Toparvion
 * @since 0.11
 */
public abstract class AbstractLogEventTypeDetector implements LogEventTypeDetector {
  private final Logger log = LoggerFactory.getLogger(getClass());

  abstract Set<LogType> getAssociatedLogTypes();
  abstract LogEventType detectLogEventTypeInternal(String eventMessage);

  @Override
  public Optional<LogEventType> detectEventType(String eventMessage, String logPath) {
    Set<LogType> associatedLogTypes = getAssociatedLogTypes();
    boolean isLogTypeMatch = associatedLogTypes.stream().anyMatch(type -> type.matches(logPath));
    if (!isLogTypeMatch) {
      return Optional.empty();
    }
    LogEventType detectedType = detectLogEventTypeInternal(eventMessage);
    if (detectedType == LogEventType.UNRECOGNIZED) {
      log.warn("Detector {} couldn't recognize log event: {}", getClass().getSimpleName(), eventMessage);
    }
    return Optional.of(detectedType);
  }
}
