package tech.toparvion.analog.service.tail;

import tech.toparvion.analog.model.TailEventType;

import static tech.toparvion.analog.model.TailEventType.*;

/**
 * A version of {@link TailSpecificsProvider} for <em>GNU Core Utils</em> {@code tail} implementation.
 * This is mostly used on Linux OS distributions as well as Cygwin ports of {@code tail} to Windows.
 * @implNote Based on <a href="http://git.savannah.gnu.org/cgit/coreutils.git/plain/src/tail.c">tail's source code</a>
 *
 * @author Toparvion
 * @since v0.7
 */
public class GnuCoreUtilsTailSpecificsProvider implements TailSpecificsProvider {

  private static final String MY_ID_STRING = "tail (GNU coreutils)";

  public static boolean matches(String idString) {
    return (idString != null) && idString.startsWith(MY_ID_STRING);
  }

  @Override
  public String getTailNativeOptions() {
    return "-F -n 0";     // follow file name with retries, starting with the very end of the file
  }

  @Override
  public long getAttemptsDelay() {
    return 5;             // this is not very useful for GNU coreutils tail as it can follow absent files itself
  }

  @Override
  public TailEventType detectEventType(String tailsMessage) throws UnrecognizedTailEventException {
    if (tailsMessage.contains("cannot open")) {
      return FILE_NOT_FOUND;
    } else if (tailsMessage.contains("has appeared") || tailsMessage.contains("has become accessible")) {
      // the file might disappear or just become inaccessible in some sense
      return FILE_APPEARED;
    } else if (tailsMessage.contains("has become inaccessible")) {
      // when file vanished; there is no other events in case the file appears again
      return FILE_DISAPPEARED;
    } else if (tailsMessage.contains("truncated")) {
      // when file decreased in size (including down to 0); consequent changes do not produce events
      return FILE_TRUNCATED;
    }
    throw new UnrecognizedTailEventException(tailsMessage);
  }
}
