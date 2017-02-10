package ru.ftc.upc.testing.analog.util.convert;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Some basic converter checks.
 * <br/>
 * Created by Toparvion on 06.02.2017.
 */
public class DateFormat2RegexConverterTest {
  private static final Logger log = LoggerFactory.getLogger(DateFormat2RegexConverterTest.class);


  private DateFormat2RegexConverter converter;

  @Before
  public void setUp() throws Exception {
    converter = new DateFormat2RegexConverter();
  }

  @Test
  public void formatWithoutFraction() throws Exception {
    String format = "dd.MM.yy HH:mm:ss";
    String sampleLogTimestamp = "02.10.14 09:21:58";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("^\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    String matchingString = matcher.group();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = formatter.parse(matchingString, LocalDateTime::from);
    String formattedRecognizedTimestamp = formatter.format(localDateTime);
    assertEquals(sampleLogTimestamp, formattedRecognizedTimestamp);
  }

  @Test
  public void formatWithFraction() throws Exception {
    String format = "uuuu-MM-dd HH:mm:ss,SSS";
    String sampleLogTimestamp = "2015-10-30 10:05:01,098";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}", convertedPattern.toString());

    String matchingString = matcher.group();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = formatter.parse(matchingString, LocalDateTime::from);
    String formattedRecognizedTimestamp = formatter.format(localDateTime);
    assertTrue(sampleLogTimestamp.contains(formattedRecognizedTimestamp));
  }

  @Test
  public void textQuotation() throws Exception {
    String format = "yyyy-MM-dd'TTT'HH:mm:ss.SSS";
    String sampleLogTimestamp = "2003-11-15TTT09:30:47.345";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("^\\d{4}\\-\\d{2}\\-\\d{2}TTT\\d{2}:\\d{2}:\\d{2}\\.\\d{3}", convertedPattern.toString());
  }

  @Test
  public void singleQuote() throws Exception {
    String format = "yyyy-MM-dd HH'h' mm'' ss'''' SSS";
    String sampleLogTimestamp = "2003-11-15 09h 30' 02'' 721";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());

    assertEquals("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}h \\d{2}' \\d{2}'' \\d{3}", convertedPattern.toString());
  }

  @Test
  public void negativeSearch() throws Exception {
    String format = "dd.MM.yy HH:mm:ss";
    String sampleLogTimestamp = "[02.10.14 09:21:58]";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);
    assertEquals("^\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertFalse(matcher.find());
  }

  @Test
  public void positiveSearch() throws Exception {
    String format = "[dd.MM.yy HH:mm:ss";
    String sampleLogTimestamp = "[02.10.14 09:21:58]";

    Pattern convertedPattern = converter.convertToRegex(format);
    log.info("Converted pattern: {}", convertedPattern);
    assertEquals("^\\[\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}", convertedPattern.toString());

    Matcher matcher = convertedPattern.matcher(sampleLogTimestamp);
    assertTrue(matcher.find());
  }
}