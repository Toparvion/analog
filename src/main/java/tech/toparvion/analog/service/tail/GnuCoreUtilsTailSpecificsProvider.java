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
  public String getCompositeTailNativeOptions(boolean includePreviousLines) {
    // follow file name with retries, starting with either the very EOF or some offset before the EOF
    return "-F -n " + (includePreviousLines ? "20" : "0");
  }

  @Override
  public String getPlainTailNativeOptions(boolean includePreviousLines) {
    // follow file name with retries, starting with either the very EOF or some offset before the EOF
    return "-F -n " + (includePreviousLines ? "45" : "0");
  }

  @Override
  public long getAttemptsDelay() {
    return 5;             // this is not very useful for GNU coreutils tail as it can follow absent files itself
  }

  @Override
  public TailEventType detectEventType(String tailsMessage) throws UnrecognizedTailEventException {
    if (tailsMessage.contains("cannot open")) {
      // this happens on the very first attempt to open a file only (not during the watching)
      return FILE_NOT_FOUND;

    } else if (tailsMessage.contains("has appeared") || tailsMessage.contains("has become accessible")) {
      // the file might disappear or just become inaccessible in some sense
      return FILE_APPEARED;

    } else if (tailsMessage.contains("has been replaced") && tailsMessage.contains("following new file")) {
      // this is when logging systems such as Logback apply their rotation policies
      return FILE_ROTATED;

    } else if (tailsMessage.contains("has become inaccessible") || tailsMessage.contains("has been replaced with an untailable")) {
      // when file vanished or became inaccessible somehow else
      return FILE_DISAPPEARED;

    } else if (tailsMessage.contains("truncated")) {
      // when file decreased in size (including down to 0); consequent change makes tail reload the whole file (!)
      return FILE_TRUNCATED;
    }
    throw new UnrecognizedTailEventException(tailsMessage);
  }
}
