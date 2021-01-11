package tech.toparvion.analog.util.timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Nullable;
import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.springframework.integration.file.FileHeaders.ORIGINAL_FILE;
import static tech.toparvion.analog.util.PathUtils.convertToUnixStyle;

/**
 * An internal tool to extract parsed timestamps from log lines.
 * Holds a registry (ako cache) of pre-configured patterns and formatters.
 *
 * @author Toparvion
 * @since v0.7
 */
@Service
public class TimestampExtractor {
  private static final Logger log = LoggerFactory.getLogger(TimestampExtractor.class);
  /**
   * By default, AnaLog parses log lines' timestamp with English locale because otherwise it may fail parsing. For
   * instance, if current system locale is 'ru' and the application tries to parse "{@code 23/Jun/2020:00:29:42}"
   * timestamp, then it will fail with the following exception:<pre><code>
   * java.time.format.DateTimeParseException: Text '23/Jun/2020:00:29:42' could not be parsed at index 3
   * </code></pre>
   * This is generally not a good idea to hard-code the locale, but it seems acceptable so far as there is no
   * localized logs in production environments (AFAIK).
   */
  public static final Locale DEFAULT_TIMESTAMP_PARSER_LOCALE = Locale.ENGLISH;
  private final DateFormat2RegexConverter converter;
  /**
   * Registry of compiled regex patterns and pre-built dateTime formatters for known logs. Registry records aren't
   * deleted upon unregistration of corresponding watchers.
   * Keys are log paths and values are {@linkplain PatternAndFormatter tuples} of prepared objects.
   */
  private final Map<String, PatternAndFormatter> registry = new HashMap<>();

  @Autowired
  public TimestampExtractor(DateFormat2RegexConverter converter) {
    this.converter = converter;
  }

  /**
   * Associates given {@code logPath} with corresponding pattern and formatter. The pattern is obtained by means of
   * {@linkplain DateFormat2RegexConverter#convertToRegex(java.lang.String) converting} given {@code format} into
   * Java regular expression. The formatter is just
   * {@linkplain DateTimeFormatter#ofPattern(java.lang.String) constructed} from the {@code format} directly.
   * @param format log timestamp format in the {@link DateTimeFormatter} syntax
   * @param logPath target log path to associate created objects with
   */
  public void registerNewTimestampFormat(String format, String logPath) {
    if (registry.containsKey(logPath)) {
      log.debug("Extractor registry already has record '{}' for logPath='{}'. Skip registration.", format, logPath);
      return;
    }
    Pattern pattern = converter.convertToRegex(format);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
                                                   .withLocale(DEFAULT_TIMESTAMP_PARSER_LOCALE);
    log.info("For logPath='{}' and its format='{}' new registry record was created: pattern='{}', formatter='{}'",
        logPath, format, pattern, formatter);
    registry.put(logPath, new PatternAndFormatter(pattern, formatter));
  }

  /**
   * Parses payload of the {@code lineMessage} against
   * {@linkplain TimestampExtractor#registerNewTimestampFormat(java.lang.String, java.lang.String) given} pattern and,
   * in case of success, returns {@link LocalDateTime} representing the timestamp specified in the line.
   * Otherwise returns {@code null}.
   * @param lineMessage a message wrapping single line of log
   * @return parsed line's timestamp or {@code null} in case of parsing fail
   */
  @Nullable
  public LocalDateTime extractTimestamp(Message<String> lineMessage) {
    String line = lineMessage.getPayload();
    if (line.startsWith("\tat ")) {
      // a kind of short-hand way to avoid wasting time on analyzing lines of java stack traces
      return null;
    }

    File logFile = lineMessage.getHeaders().get(ORIGINAL_FILE, File.class);
    Assert.isTrue(logFile != null, "lineMessage doesn't contain 'file_originalFile' header; check tailAdapter.");

    String logPath = convertToUnixStyle(logFile.getAbsolutePath());
    PatternAndFormatter paf = registry.get(logPath);
    Assert.isTrue(paf != null, format("Log path '%s' is not registered but its line message was received.", logPath));

    Matcher timestampMatcher = paf.getPattern().matcher(line);
    if (!timestampMatcher.find()) {
      return null;
      // TODO есть проблема: если число таких записей без метки будет слишком велико, то выделяющий записи агрегатор
      //  выпустит их без "головы", то есть без предшествующей записи с меткой. Из-за этого на принимающей стороне их,
      //  возможно, будет трудно куда-либо определить. Нужно подумать, как это победить, и есть ли такая проблема.
    }

    String tsString = timestampMatcher.group();
    DateTimeFormatter formatter = paf.getFormatter();
    LocalDateTime parsedTimestamp;
    try {
      parsedTimestamp = formatter.parse(tsString, LocalDateTime::from);
    } catch (DateTimeException e) {
      log.debug("Unable to parse timestamp string '{}' with formatter '{}'.", tsString, formatter, e);
      // in case no date specified in timestamp format, AnaLog supposes the date to be equal to the current one
      LocalTime parsedTime = formatter.parse(tsString, LocalTime::from);
      parsedTimestamp = LocalDateTime.of(LocalDate.now(Clock.systemDefaultZone()), parsedTime);
    }

    return parsedTimestamp;
  }

  static class PatternAndFormatter {
    private final Pattern pattern;
    private final DateTimeFormatter formatter;

    PatternAndFormatter(Pattern pattern, DateTimeFormatter formatter) {
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

  /*public*/ Map<String, PatternAndFormatter> getRegistry() {
    return registry;
  }
}
