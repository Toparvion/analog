package tech.toparvion.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Toparvion
 * @since v0.9
 */
@SuppressWarnings({"unused", "WeakerAccess"})     // such access level and setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class RecordLevelsProperties {
  private List<String> recordLevels = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

  public List<String> getRecordLevels() {
    return recordLevels;
  }

  public void setRecordLevels(List<String> recordLevels) {
    this.recordLevels = recordLevels;
  }

}
