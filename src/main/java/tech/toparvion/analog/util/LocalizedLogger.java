package tech.toparvion.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

public class LocalizedLogger {

  private final Logger log;
  private final MessageSource messageSource;


  public LocalizedLogger(Object caller, MessageSource messageSource) {
    this.log = LoggerFactory.getLogger(caller.getClass());
    this.messageSource = messageSource;
  }

  private String resolveTemplate(String key) {
    return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
  }

  public void trace(String key, Object... args) {
    log.trace(resolveTemplate(key), args);
  }

  public void debug(String key, Object... args) {
    log.debug(resolveTemplate(key), args);
  }

  public void info(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

  public void warn(String key, Object... args) {
    log.warn(resolveTemplate(key), args);
  }

  public void error(String key, Object... args) {
    log.error(resolveTemplate(key), args);
  }

}
