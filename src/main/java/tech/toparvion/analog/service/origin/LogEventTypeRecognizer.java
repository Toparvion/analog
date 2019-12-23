package tech.toparvion.analog.service.origin;

import tech.toparvion.analog.model.LogEventType;

import java.util.Optional;

/**
 * @author Toparvion
 * @since 0.11
 */
interface LogEventTypeRecognizer {

  Optional<LogEventType> detectEventType(String eventMessage, String logPath);
}
