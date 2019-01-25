package tech.toparvion.analog.remote.server.origin.detect;

import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.config.entry.LogType;

import java.util.Set;

import static tech.toparvion.analog.model.config.entry.LogType.DOCKER;

/**
 * @author Toparvion
 * @since 0.11
 */
@Service
public class DockerLogEventTypeDetector extends AbstractLogEventTypeDetector {

  @Override
  protected Set<LogType> getAssociatedLogTypes() {
    return Set.of(DOCKER);
  }

  @Override
  protected LogEventType detectLogEventTypeInternal(String eventMessage) {
    if (eventMessage.contains("No such container")) {
      return LogEventType.LOG_NOT_FOUND;
    }

    return LogEventType.UNRECOGNIZED;
  }
}
