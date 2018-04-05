package tech.toparvion.analog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.toparvion.analog.model.RecordLevel;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Toparvion
 * @since v0.8.1
 */
class RecordLevelDetectorTest {

  private RecordLevelDetector sut;

  @BeforeEach
  void setUp() {
    Set<String> knownLevels = Arrays.stream(RecordLevel.values())
        .map(RecordLevel::toString)
        .collect(toSet());

    sut = new RecordLevelDetector(knownLevels);
  }

  @Test
  @DisplayName("INFO level; single line; payload containing DEBUG")
  void detectLevel_1() {
    String record = "03.04.18 07:20:30 INFO [Thread-1] TestSampler - This is a DEBUG message";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    String detectedLevel = levelOpt.get();
    assertEquals("INFO", detectedLevel);
  }

  @Test
  @DisplayName("TRACE level; single line; payload containing TRACE")
  void detectLevel_2() {
    String record = "03.04.18 07:20:30 TRACE [Thread-1] TestSampler - This is a TRACE message";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    String detectedLevel = levelOpt.get();
    assertEquals("TRACE", detectedLevel);
  }

  @Test
  @DisplayName("TRACE level; single line; incorrect level name is used at the start of line")
  void detectLevel_3() {
    String record = "03.04.18 info 07:20:30 TRACE [Thread-1] TestSampler - This is a log message";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    String detectedLevel = levelOpt.get();
    assertEquals("TRACE", detectedLevel);
  }

  @Test
  @DisplayName("WARN level; multi line")
  void detectLevel_4() {
    String record = "03.04.18 07:20:30 WARN [Thread-1] TestSampler - This is a log message\n" +
        "ERROR - And this is the second floor of the record";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    String detectedLevel = levelOpt.get();
    assertEquals("WARN", detectedLevel);
  }

  @Test
  @DisplayName("FATAL level; single line; level mark is the very beginning of the line")
  void detectLevel_5() {
    String record = "FATAL [Thread-1] TestSampler - This is a log message that starts with FATAL";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    String detectedLevel = levelOpt.get();
    assertEquals("FATAL", detectedLevel);
  }

  @Test
  @DisplayName("Unknown level; single line; no level mark is contained in the message")
  void detectLevel_6() {
    String record = "03.04.18 07:20:30 [Thread-1] TestSampler - This is a log message";
    Optional<String> levelOpt = sut.detectLevel(record);
    assertFalse(levelOpt.isPresent());
  }

  @Test
  @DisplayName("A proof-of-concept for issue #6 (https://github.com/Toparvion/analog/issues/6)")
  void detectLevel_7() {
    String record = "2018-02-17 23:53:29.115 DEBUG 61348 --- [on(2)-10.0.63.8] o.s.m.s.b.SimpleBrokerMessageHandler : " +
        "Processing MESSAGE destination=/topic//Users/mnatikk/app/analog-v0.8.1/console.out session=null payload=" +
        "{\"lines\":[{\"text\":\"2018-02-17 23:53:28.604 INFO 61348 --- [ask-scheduler-8] o.s...(truncated)";

    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }
}