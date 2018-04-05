package tech.toparvion.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Toparvion
 * @since v0.8.1
 */
public class RecordLevelDetector {
  private static final Logger log = LoggerFactory.getLogger(RecordLevelDetector.class);

  private final Set<Pattern> levelPatterns = new HashSet<>();

  public RecordLevelDetector(Set<String> knownLevels) {
    for (String level: knownLevels) {
      Pattern pattern = Pattern.compile("^.*\\b(" + level + ")\\b" /*, DOTALL, MULTILINE <- deliberately suppressed*/);
      levelPatterns.add(pattern);
    }
    log.info("Following patterns will be used to detect record levels: {}", levelPatterns);
  }

  public Optional<String> detectLevel(String record) {
    Map<Integer, String> results = new HashMap<>();
    for (Pattern levelPattern : levelPatterns) {
      Matcher matcher = levelPattern.matcher(record);
      if (!matcher.find()) {
        continue;
      }
      String foundLevel = matcher.group(1);
      results.put(matcher.start(1), foundLevel);
    }

    final int NOT_FOUND = -1;
    int minimumLevelIndex = results.keySet()
        .stream()
        .mapToInt(Integer::intValue)
        .min()
        .orElse(NOT_FOUND);

    return (minimumLevelIndex != NOT_FOUND)
        ? Optional.of(results.get(minimumLevelIndex))
        : Optional.empty();
  }

}
