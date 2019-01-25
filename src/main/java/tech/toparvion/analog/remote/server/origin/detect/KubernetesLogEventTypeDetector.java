package tech.toparvion.analog.remote.server.origin.detect;

import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.config.entry.LogType;

import java.util.Set;

import static tech.toparvion.analog.model.config.entry.LogType.K8S;
import static tech.toparvion.analog.model.config.entry.LogType.KUBERNETES;

/**
 * @author Toparvion
 * @since 0.11
 */
@Service
public class KubernetesLogEventTypeDetector extends AbstractLogEventTypeDetector {

  @Override
  Set<LogType> getAssociatedLogTypes() {
    return Set.of(KUBERNETES, K8S);
  }

  @Override
  LogEventType detectLogEventTypeInternal(String eventMessage) {
    if (eventMessage.contains("NotFound")) {
      return LogEventType.LOG_NOT_FOUND;
    }

    return LogEventType.UNRECOGNIZED;
  }
}
