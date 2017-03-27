package ru.ftc.upc.testing.analog.util.timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.FileHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class TimestampExtractor {
  private static final Logger log = LoggerFactory.getLogger(TimestampExtractor.class);

  private final DateFormat2RegexConverter converter;
  /**
   * Registry of compiled regex patterns and pre-built dateTime formatters for known logs. Registry records aren't
   * deleted upon unregistration of corresponding watchers.
   * Keys are log paths and values are tuples of prepared objects.
   */
  private final Map<String, PatternAndFormatter> registry = new HashMap<>();

  @Autowired
  public TimestampExtractor(DateFormat2RegexConverter converter) {
    this.converter = converter;
  }

  /**
   *
   * @param format
   * @param logPath
   */
  public void registerNewTimestampFormat(String format, String logPath) {
    if (registry.containsKey(logPath)) {
      log.debug("Extractor registry already has a record for logPath='{}'. Skip registration.", logPath);
      return;
    }
    Pattern pattern = converter.convertToRegex(format);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    log.info("For logPath='{}' and its format='{}' new registry record was created: pattern='{}', formatter='{}'",
        logPath, format, pattern, formatter);
    registry.put(logPath, new PatternAndFormatter(pattern, formatter));
  }

  /**
   *
   * @param lineMessage
   * @return
   */
  public LocalDateTime extractTimestamp(Message<String> lineMessage) {
    String line = lineMessage.getPayload();
    if (line.startsWith("\tat ")) {
      // a kind of short-hand way to avoid wasting time on analyzing lines of java stacktraces
      return null;
    }

    File logFile = lineMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE, File.class);
    assert (logFile != null) : "lineMessage doesn't contain 'file_originalFile' header; check tailAdapter.";

    PatternAndFormatter paf = registry.get(logFile.getAbsolutePath());
    assert (paf != null) : format("Log path '%s' is not registered but its line message was received.", logFile.getAbsolutePath());

    Matcher timestampMatcher = paf.getPattern().matcher(line);
    if (!timestampMatcher.find()) {
      return null;
      // TODO есть проблема: если число таких записей без метки будет слишком велико, то выделяющий записи агрегатор
      // выпустит их без "головы", то есть без предшествующей записи с меткой. Из-за этого на принимающей стороне их,
      // возможно, будет трудно куда-либо определить. Нужно подумать, как это победить и есть ли такая проблема.
    }

    String tsString = timestampMatcher.group();
    return paf.getFormatter().parse(tsString, LocalDateTime::from);
  }

  private static class PatternAndFormatter {
    private final Pattern pattern;
    private final DateTimeFormatter formatter;

    private PatternAndFormatter(Pattern pattern, DateTimeFormatter formatter) {
      this.pattern = pattern;
      this.formatter = formatter;
    }

    Pattern getPattern() {
      return pattern;
    }

    DateTimeFormatter getFormatter() {
      return formatter;
    }
  }

}
