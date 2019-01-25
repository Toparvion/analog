package tech.toparvion.analog.remote.server.origin.detect;

import tech.toparvion.analog.model.LogEventType;

import java.util.Optional;

/**
 * @author Toparvion
 * @since 0.11
 */
public interface LogEventTypeDetector {

  Optional<LogEventType> detectEventType(String eventMessage, String logPath);
}
