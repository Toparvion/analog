package ru.ftc.upc.testing.analog.service.tail;

import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.file.tail.OSDelegatingFileTailingMessageProducer;
import ru.ftc.upc.testing.analog.model.TailEventType;

/**
 * An abstraction covering details of tail's implementation on various platforms (Linux, Unix, ...)
 *
 * @author Toparvion
 * @since v0.7
 */
public interface TailSpecificsProvider {

  /**
   * @return string that must be used as an options set for proper tail invocation on current platform
   * @see OSDelegatingFileTailingMessageProducer#setOptions(java.lang.String)
   */
  String getTailNativeOptions();

  /**
   * @return delay in milliseconds between attempts to read non-accessible or non-existent file
   * @see FileTailingMessageProducerSupport#setTailAttemptsDelay(long)
   */
  long getAttemptsDelay();

  /**
   * @param tailsMessage a message produced by tail while following a file
   * @return corresponding {@link TailEventType} supported by AnaLog
   * @throws UnrecognizedTailEventException in case of unknown event
   */
  TailEventType detectEventType(String tailsMessage) throws UnrecognizedTailEventException;
}
