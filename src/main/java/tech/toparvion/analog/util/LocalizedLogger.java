package tech.toparvion.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import java.util.Locale;

public class LocalizedLogger {

  private final Logger log;
  private final MessageSource messageSource;
  private final Locale locale;

  public LocalizedLogger(Object caller, MessageSource messageSource) {
    this(caller, messageSource, new Locale("ru", "RU"));
  }

  public LocalizedLogger(Object caller, MessageSource messageSource, Locale locale) {
    this.log = LoggerFactory.getLogger(caller.getClass());
    this.messageSource = messageSource;
    this.locale = locale;
  }

  private String resolveTemplate(String key) {
    return messageSource.getMessage(key, null, key, locale);
  }

  public void trace(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

  public void debug(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

  public void info(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

  public void warn(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

  public void error(String key, Object... args) {
    log.info(resolveTemplate(key), args);
  }

}
