package tech.toparvion.analog.service.origin;

import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.config.entry.LogType;

import javax.annotation.Nullable;
import java.util.Set;

import static tech.toparvion.analog.model.LogEventType.*;
import static tech.toparvion.analog.model.config.entry.LogType.LOCAL_FILE;
import static tech.toparvion.analog.model.config.entry.LogType.NODE;

/**
 * @author Toparvion
 * @since 0.11
 * @implNote Based on <a href="http://git.savannah.gnu.org/cgit/coreutils.git/plain/src/tail.c">tail's source code</a>
 * @see <a href="https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man1/tail.1.html">OS X Man Pages</a>
 */
@Service
class FileLogEventTypeRecognizer extends AbstractLogEventTypeRecognizer {

  @Override
  Set<LogType> getAssociatedLogTypes() {
    return Set.of(LOCAL_FILE, NODE);
  }

  @Override
  LogEventType detectLogEventTypeInternal(String eventMessage) {
    LogEventType detectedLogEventType;
    if ((detectedLogEventType = tryDetectForGnuCoreUtils(eventMessage)) != null) {
      return detectedLogEventType;
    }
    if ((detectedLogEventType = tryDetectForMac(eventMessage)) != null) {
      return detectedLogEventType;
    }
    if ((detectedLogEventType = tryDetectForSolaris(eventMessage)) != null) {
      return detectedLogEventType;
    }
    return UNRECOGNIZED;
  }

  @Nullable
  private LogEventType tryDetectForGnuCoreUtils(String eventMessage) {
    if (eventMessage.contains("cannot open")) {
      // this happens on the very first attempt to open a file only (not during the watching)
      return LOG_NOT_FOUND;

    } else if (eventMessage.contains("has appeared") || eventMessage.contains("has become accessible")) {
      // the file might disappear or just become inaccessible in some sense
      return LOG_APPEARED;

    } else if (eventMessage.contains("has been replaced") && eventMessage.contains("following new file")) {
      // this is when logging systems such as Logback apply their rotation policies
      return LOG_ROTATED;

    } else if (eventMessage.contains("has become inaccessible") || eventMessage.contains("has been replaced with an untailable")) {
      // when file vanished or became inaccessible somehow else
      return LOG_DISAPPEARED;

    } else if (eventMessage.contains("truncated")) {
      // when file decreased in size (including down to 0); consequent change makes tail reload the whole file (!)
      return LOG_TRUNCATED;
    }
    return null;
  }

  @Nullable
  private LogEventType tryDetectForMac(String eventMessage) {
    if (eventMessage.contains("No such file")) {
      return LOG_NOT_FOUND;
    }
    return null;
  }

  @Nullable
  private LogEventType tryDetectForSolaris(String eventMessage) {
    if (eventMessage.contains("cannot open input")) {
      return LOG_NOT_FOUND;
    }
    return null;
  }
}
