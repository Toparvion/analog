package tech.toparvion.analog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.toparvion.analog.model.config.RecordLevelsProperties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Toparvion
 * @since v0.9
 */
class RecordLevelDetectorTest {

  private RecordLevelsProperties getPropsFor(String... levels) {
    RecordLevelsProperties props = new RecordLevelsProperties();
    props.setRecordLevels(List.of(levels));
    return props;
  }

  @Test
  void detectLevel_1() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_2() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("   DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_3() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("   DEBUG   ");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_4() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUGGER");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  void detectLevel_5() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEB");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  void detectLevel_6() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("antiDEBUG");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  void detectLevel_7() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("otherWord DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_8() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("otherWord DEBUG anotherWord");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_9() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("_ DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_10() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("otherWord\n DEBUG");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  void detectLevel_11() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("\n DEBUG");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  void detectLevel_12() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG\n");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_13() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "INFO"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG INFO");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_14() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "INFO"), true);
    Optional<String> levelOpt = sut.detectLevel("INFO DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("INFO", levelOpt.get());
  }

  @Test
  void detectLevel_15() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("INFO", "DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG INFO");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_16() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("INFO", "DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("INFO DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("INFO", levelOpt.get());
  }

  @Test
  void detectLevel_17() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "INFO"), true);
    Optional<String> levelOpt = sut.detectLevel("INFO DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("INFO", levelOpt.get());
  }

  @Test
  void detectLevel_18() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "INFO", "DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("INFO DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("INFO", levelOpt.get());
  }

  @Test
  void detectLevel_19() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new RecordLevelDetector(getPropsFor("DEBUG", "INFO%"), true));
    assertTrue(exception.getMessage().contains("'%'"));
    assertTrue(exception.getMessage().contains("'INFO%'"));
  }

  @Test
  void detectLevel_20() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "DEBUGGING"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG DEBUGGING");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_22() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG", "DEBUGGING"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUGGING DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUGGING", levelOpt.get());
  }

  @Test
  void detectLevel_23() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUGGING", "DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUG DEBUGGING");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUGGING", levelOpt.get());
    // It is quite natural to expect here DEBUG level to be detected as its mention comes first in given string.
    // But there are 3 things to consider:
    // 1. DEBUGGING level is declared higher than DEBUG in record level list;
    // 2. DEBUGGING level matches the word being analyzed ("DEBUG") at its start;
    // 3. analyzeWord() method takes the very first matched level's index as winner.
    // Together these things make DEBUGGING level be selected. While this is not absolutely correct, such a situation
    // can happen only when there are at least two levels starting with the same letters. Because it is quite rare
    // case the behavior is considered acceptable.
  }

  @Test
  void detectLevel_24() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUGGING", "DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("DEBUGGING DEBUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUGGING", levelOpt.get());
  }

  @Test
  @DisplayName("A proof-of-concept for issue #6 (https://github.com/Toparvion/analog/issues/6)")
  void detectLevel_25() {
    String record = "2018-02-17 23:53:29.115 DEBUG 61348 --- [on(2)-10.0.63.8] o.s.m.s.b.SimpleBrokerMessageHandler : " +
        "Processing MESSAGE destination=/topic//Users/mnatikk/app/analog-v0.8.1/console.out session=null payload=" +
        "{\"lines\":[{\"text\":\"2018-02-17 23:53:28.604 INFO 61348 --- [ask-scheduler-8] o.s...(truncated)";
    RecordLevelDetector sut = new RecordLevelDetector(
        getPropsFor("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"), true);
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_26() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), false);
    Optional<String> levelOpt = sut.detectLevel("dEbUG");
    assertTrue(levelOpt.isPresent());
    assertEquals("DEBUG", levelOpt.get());
  }

  @Test
  void detectLevel_27() {
    RecordLevelDetector sut = new RecordLevelDetector(getPropsFor("DEBUG"), true);
    Optional<String> levelOpt = sut.detectLevel("deBUG");
    assertFalse(levelOpt.isPresent());
  }

  @Test
  @DisplayName("A proof-of-concept for issue with TRACE_ID")
  void detectLevel_28() {
    String record = "[2019-11-07 10:45:00,000][INFO ][asyncTaskThread-1][BRAND:, TRACE_ID:, SESSION_KEY:, RID:, USER_ID:, PHONE:, FEE:, PROCESS:] [ftc.feeonline.shared.async.task.AsyncTaskAspect] [Task PREFILL_ERROR_EMAIL_SEND started]";
    RecordLevelDetector sut = new RecordLevelDetector(
        getPropsFor("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"), true);
    Optional<String> levelOpt = sut.detectLevel(record);
    assertTrue(levelOpt.isPresent());
    assertEquals("INFO", levelOpt.get());
  }
  
  
}