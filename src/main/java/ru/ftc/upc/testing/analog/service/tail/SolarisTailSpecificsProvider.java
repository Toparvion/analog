package ru.ftc.upc.testing.analog.service.tail;

import ru.ftc.upc.testing.analog.model.TailEventType;

import static ru.ftc.upc.testing.analog.model.TailEventType.FILE_NOT_FOUND;

/**
 * tail's specifics for Solaris OS family. Actually there are different tail implementations for various Solaris
 * distributions (SunOS, IllumOs, etc) but AnaLog doesn't need to distinguish them so far.
 *
 * @author Toparvion
 * @since v0.7
 */
public class SolarisTailSpecificsProvider implements TailSpecificsProvider {

  private static final String MY_IDF_STRING = "usage: tail";

  public static boolean matches(String idfString) {
    return (idfString != null) && idfString.startsWith(MY_IDF_STRING);
  }

  @Override
  public String getTailNativeOptions() {
    return "-0f";     // -F option is not supported on SunOS
  }

  @Override
  public long getAttemptsDelay() {
    return 5000;      // Solaris tail is not capable of automatic retrying so that AnaLog takes it on itself
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
