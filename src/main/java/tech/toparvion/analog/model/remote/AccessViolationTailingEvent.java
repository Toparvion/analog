package tech.toparvion.analog.model.remote;

import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;

import java.io.File;

/**
 * @author Toparvion
 * @since v0.12
 */
public class AccessViolationTailingEvent extends FileTailingEvent {
  
  public AccessViolationTailingEvent(Object source, String message, File file) {
    super(source, message, file);
  }
}
