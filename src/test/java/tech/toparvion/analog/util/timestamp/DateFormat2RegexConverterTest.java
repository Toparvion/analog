package tech.toparvion.analog.util.timestamp;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Some basic converter checks.
 * <br/>
 * Created by Toparvion on 06.02.2017.
 */
class DateFormat2RegexConverterTest {
  private static final Logger log = LoggerFactory.getLogger(DateFormat2RegexConverterTest.class);


  private DateFormat2RegexConverter converter;

  @BeforeEach
  void setUp() {
    converter = new DateFormat2RegexConverter();
  }

  @Test
  void formatWithoutFraction() {
    String format = "dd.MM.yy HH:mm:ss";
    String sampleLogTimestamp = "02.10.14 09:21:58";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    String matchingString = matcher.group();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = formatter.parse(matchingString, LocalDateTime::from);
    String formattedRecognizedTimestamp = formatter.format(localDateTime);
    assertEquals(sampleLogTimestamp, formattedRecognizedTimestamp);
  }

  @Test
  void formatWithFraction() {
    String format = "uuuu-MM-dd HH:mm:ss,SSS";
    String sampleLogTimestamp = "2015-10-30 10:05:01,098";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}", convertedPattern.toString());

    String matchingString = matcher.group();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = formatter.parse(matchingString, LocalDateTime::from);
    String formattedRecognizedTimestamp = formatter.format(localDateTime);
    assertTrue(sampleLogTimestamp.contains(formattedRecognizedTimestamp));
  }

  @Test
  void textQuotation() {
    String format = "yyyy-MM-dd'TTT'HH:mm:ss.SSS";
    String sampleLogTimestamp = "2003-11-15TTT09:30:47.345";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("\\d{4}\\-\\d{2}\\-\\d{2}TTT\\d{2}:\\d{2}:\\d{2}\\.\\d{3}", convertedPattern.toString());
  }

  @Test
  void singleQuote() {
    String format = "yyyy-MM-dd HH'h' mm'' ss'''' SSS";
    String sampleLogTimestamp = "2003-11-15 09h 30' 02'' 721";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}h \\d{2}' \\d{2}'' \\d{3}", convertedPattern.toString());
  }

  @Test
  void positiveSearch() {
    String format = "dd.MM.yy HH:mm:ss";
    String sampleLogTimestamp = "[02.10.14 09:21:58]";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);
    assertEquals("\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());
  }

  @Test
  void tripleLetterMonthSearch() {
    String format = "dd/LLL/yyyy:HH:mm:ss";
    String sampleLogTimestamp = "192.168.95.59 - - [29/Dec/2018:03:00:40 +0700] \"GET";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);
//    assertEquals("\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());
  }

  @Test
  void letterSeparatedSearch() {
    String format = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    String sampleLogTimestamp = "{\"timestamp\":\"2018-12-29T12:05:18.021+0700\"";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);
//    assertEquals("\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());
  }

  @Test
  void testAmPmFormatter() {
    var format = "LLL dd, yyyy K:mm:ss";
    var formatter = DateTimeFormatter.ofPattern(format)
                                                    .withLocale(ENGLISH);
    ThrowingCallable sutCall = () -> formatter.parse("Dec 21, 2020 07:05:00");
//    log.info("Parsed dateTime: {}", parsedDateTime);
    assertThatCode(sutCall).doesNotThrowAnyException();
  }

  @Test
  void testNginxLogFormat() {
    var format = "dd/LLL/yyyy:HH:mm:ss";
    var formatter = DateTimeFormatter.ofPattern(format)
                                                    .withLocale(ENGLISH);
    ThrowingCallable sutCall = () -> formatter.parse("23/Jun/2020:00:29:42");
//    log.info("Parsed dateTime: {}", parsedDateTime);
    assertThatCode(sutCall).doesNotThrowAnyException();
  }
}