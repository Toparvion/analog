/*
package tech.toparvion.analog.util.timestamp;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.integration.support.MessageBuilder;
import tech.toparvion.analog.util.timestamp.TimestampExtractor.PatternAndFormatter;

import java.io.File;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;
import static org.springframework.integration.file.FileHeaders.ORIGINAL_FILE;
import static tech.toparvion.analog.util.timestamp.TimestampExtractor.DEFAULT_TIMESTAMP_PARSER_LOCALE;

*/
/**
 * @author Toparvion
 *//*

class TimestampExtractorTest {

  DateFormat2RegexConverter converter;
  TimestampExtractor sut;

  @BeforeEach
  void setUp() {
    converter = mock(DateFormat2RegexConverter.class);
    sut = new TimestampExtractor(converter);
  }

  @Test
  @DisplayName("A format for given log path is already registered")
  void registerNewTimestampFormat_existentFormat() {
    // given
    var format = "someFormat";
    var logPath = "logPath";
    sut.getRegistry().put(logPath, mock(PatternAndFormatter.class));

    // when
    sut.registerNewTimestampFormat(format, logPath);

    // then
    verifyNoInteractions(converter);
    assertThat(sut.getRegistry()).size().isEqualTo(1);
  }
  @Test
  @DisplayName("A format for given log path is stored into the map")
  void registerNewTimestampFormat_newFormat() {
    // given
    var format = "dd/LLL/yyyy:HH:mm:ss";
    var logPath = "logPath";
    Pattern pattern = Pattern.compile("");
    when(converter.convertToRegex(format)).thenReturn(pattern);

    // when
    sut.registerNewTimestampFormat(format, logPath);

    // then
    verify(converter).convertToRegex(format);
    Condition<PatternAndFormatter> pafCondition = new Condition<>() {
      public boolean matches(PatternAndFormatter paf) {
        return paf.getFormatter().getLocale().equals(DEFAULT_TIMESTAMP_PARSER_LOCALE)
            && paf.getPattern().equals(pattern);
      }
    };
    assertThat(sut.getRegistry()).hasSize(1)
        .hasEntrySatisfying(logPath, pafCondition);

  }

  @Test
  @DisplayName("The extracting exits shortly with null if the line starts with '\\tat'")
  void extractTimestamp_shortHand() {
    // given
    var message = MessageBuilder.withPayload("\tat SomeElse.java").build();

    // when
    LocalDateTime timestamp = sut.extractTimestamp(message);

    // then
    assertThat(timestamp).isNull();
  }

  @Test
  @DisplayName("The extracting returns null if no matched group found")
  void extractTimestamp_noPafFound() {
    // given
    var logFile = mock(File.class);
    var logPath = "/home/me/myapp/app.log";
    when(logFile.getAbsolutePath()).thenReturn(logPath);
    var message = MessageBuilder
        .withPayload("2020-05-10 09:23:05,419  INFO [main] - tech.toparvion.analog.AnaLog")
        .setHeader(ORIGINAL_FILE, logFile)
        .build();
    PatternAndFormatter paf = mock(PatternAndFormatter.class);
    Pattern pattern = Pattern.compile("neverMatchingPattern");
    when(paf.getPattern()).thenReturn(pattern);
    sut.getRegistry().put(logPath, paf);

    // when
    LocalDateTime timestamp = sut.extractTimestamp(message);

    //then
    assertThat(timestamp).isNull();
    //noinspection ResultOfMethodCallIgnored
    verify(logFile).getAbsolutePath();
    //noinspection ResultOfMethodCallIgnored
    verify(paf).getPattern();
    verifyNoMoreInteractions(paf);
  }

  @Test
  @DisplayName("The extracting ends up with successfully parsed timestamp")
  void extractTimestamp_successfulParsing() {
    // given
    var logFile = mock(File.class);
    var logPath = "/home/me/myapp/app.log";
    when(logFile.getAbsolutePath()).thenReturn(logPath);
    var message = MessageBuilder
        .withPayload("02.10.14 09:21:58  INFO [main] - tech.toparvion.analog.AnaLog")
        .setHeader(ORIGINAL_FILE, logFile)
        .build();
    var pattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss");
    PatternAndFormatter paf = new PatternAndFormatter(pattern, formatter);
    sut.getRegistry().put(logPath, paf);

    // when
    LocalDateTime timestamp = sut.extractTimestamp(message);

    //then
    assertThat(timestamp)
        .isNotNull()
        .isEqualTo("2014-10-02T09:21:58");
    //noinspection ResultOfMethodCallIgnored
    verify(logFile).getAbsolutePath();
  }

  @Test
  @DisplayName("The extracting can use current date if no date is specified in the log message")
  void extractTimestamp_noDateSpecified() {
    // given
    var logFile = mock(File.class);
    var logPath = "/home/me/myapp/app.log";
    when(logFile.getAbsolutePath()).thenReturn(logPath);
    var message = MessageBuilder
        .withPayload("19:21:58 INFO [main] - tech.toparvion.analog.AnaLog")
        .setHeader(ORIGINAL_FILE, logFile)
        .build();
    var pattern = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    PatternAndFormatter paf = new PatternAndFormatter(pattern, formatter);
    sut.getRegistry().put(logPath, paf);

    // when
    LocalDateTime timestamp = sut.extractTimestamp(message);

    //then
    LocalDateTime logTimeWithCurrentDate =
        LocalDateTime.of(LocalDate.now(Clock.systemDefaultZone()), LocalTime.of(19, 21, 58));
    assertThat(timestamp)
        .isNotNull()
        .isCloseTo(logTimeWithCurrentDate, within(1, SECONDS));

    //noinspection ResultOfMethodCallIgnored
    verify(logFile).getAbsolutePath();
  }
}*/
