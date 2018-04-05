package tech.toparvion.analog.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.toparvion.analog.model.RecordLevel;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * @author Toparvion
 * @since v0.8.1
 */
@Configuration
public class ServiceConfig {

  /**
   * For the time being (v0.8.1) RecordLevelDetector is already capable of detection basing on any set of levels. But
   * the rest application infrastructure (properties and web client) are not ready for that. Therefore
   * RecordLevelDetector is created by explicitly supplying it with {@link RecordLevel} enumeration so far.
   */
  @Bean
  public RecordLevelDetector recordLevelDetector() {
    Set<String> knownLevels = Arrays.stream(RecordLevel.values())
        .map(RecordLevel::toString)
        .collect(toSet());
    return new RecordLevelDetector(knownLevels);
  }
}
