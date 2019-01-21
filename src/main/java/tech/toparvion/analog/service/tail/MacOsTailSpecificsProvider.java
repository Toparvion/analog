package tech.toparvion.analog.service.tail;

import tech.toparvion.analog.model.TailEventType;

import static tech.toparvion.analog.model.TailEventType.FILE_NOT_FOUND;

/**
 * A version of {@link TailSpecificsProvider} for <em>MacOS</em> {@code tail} implementation.
 *
 * @see <a href="https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man1/tail.1.html">OS X Man Pages</a>
 * @author Toparvion
 * @since v0.7
 */
public class MacOsTailSpecificsProvider implements TailSpecificsProvider {

  private static final String MY_ID_STRING = "illegal option --";

  public static boolean matches(String idString) {
    return (idString != null) && idString.contains(MY_ID_STRING);
  }

  @Override
  public String getGroupTailNativeOptions(boolean includePreviousLines) {
    // follow file name with retries, starting with either the very EOF or some offset before the EOF
    return "-F -" + (includePreviousLines ? "20" : "0");
  }

  @Override
  public String getFlatTailNativeOptions(boolean includePreviousLines) {
    // follow file name with retries, starting with either the very EOF or some offset before the EOF
    return "-F -" + (includePreviousLines ? "45" : "0");
  }

  @Override
  public long getAttemptsDelay() {
    return 5;             // this is not very useful for GNU coreutils tail as it can follow absent files itself
  }

  @Override
  public TailEventType detectEventType(String tailsMessage) throws UnrecognizedTailEventException {
    if (tailsMessage.contains("cannot open")) {
      return FILE_NOT_FOUND;
    }
    // TODO find out and apply other types of events on this platform
    throw new UnrecognizedTailEventException(tailsMessage);
  }
}
