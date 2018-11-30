package tech.toparvion.analog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.toparvion.analog.util.AnaLogUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Toparvion
 * @since v0.7
 */
class AnaLogUtilsTest {
  private static final Logger log = LoggerFactory.getLogger(AnaLogUtilsTest.class);

  @Test
  @DisplayName("XML header is not treated as beginning of XML document")
  void distinguishXmlComposite1() {
    String originalRecord =
        "23.10.12 19:18:14 [TP-Processor7] DEBUG com.ftc.bankplus.loyalty.LoyaltyClient SCT:13a8d8eeddd - answer: <?xml version='1.0' encoding='UTF-8'?>\n" +
            "<response xmlns=\"http://ftc.ru/loyalty/api/standard-response/generic/1.x\">\n" +
            "<code>OK</code>\n" +
            "</response>";
    log.info("Original record:\n{}", originalRecord);
    String[] originalTokens = originalRecord.split("\n");
    List<String> lines = new ArrayList<>(asList(originalTokens));
    String startingLine = AnaLogUtils.distinguishXmlComposite(lines, 0);
    log.info("Starting line: '{}'", startingLine);
    String processedRecord = String.join("\n", lines);
    log.info("Processed record:\n{}", processedRecord);

    assertEquals(originalRecord, processedRecord);
    assertEquals(originalTokens[0], startingLine);
  }

  @Test
  @DisplayName("Valid XML document without non-XML prefix/postfix")
  void distinguishXmlComposite2() {
    String originalRecord =
            "<response xmlns=\"http://ftc.ru/loyalty/api/standard-response/generic/1.x\">\n" +
            "<code>OK</code>\n" +
            "</response>";
    log.info("Original record:\n{}", originalRecord);
    String[] originalTokens = originalRecord.split("\n");
    List<String> lines = new ArrayList<>(asList(originalTokens));
    String startingLine = AnaLogUtils.distinguishXmlComposite(lines, 0);
    log.info("Starting line: '{}'", startingLine);
    String processedRecord = String.join("\n", lines);
    log.info("Processed record:\n{}", processedRecord);

    assertTrue(startingLine.startsWith("__XML__"));
    assertTrue(processedRecord.startsWith("__XML__" + originalTokens[0]));
  }

  @Test
  @DisplayName("Valid XML document with both non-XML prefix and postfix")
  void distinguishXmlComposite3() {
    String originalRecord =
            "<?xml version='1.0' encoding='UTF-8'?><response xmlns=\"http://ftc.ru/loyalty/api/standard-response/generic/1.x\">\n" +
            "<code>OK</code>\n" +
            "</response>\n" +
            "...and something else...";
    log.info("Original record:\n{}", originalRecord);
    String[] originalTokens = originalRecord.split("\n");
    List<String> lines = new ArrayList<>(asList(originalTokens));
    String startingLine = AnaLogUtils.distinguishXmlComposite(lines, 0);
    log.info("Starting line: '{}'", startingLine);
    String processedRecord = String.join("\n", lines);
    log.info("Processed record:\n{}", processedRecord);

    assertEquals("<?xml version='1.0' encoding='UTF-8'?>", startingLine);
    assertTrue(lines.get(1).startsWith("__XML__"));
    assertTrue(processedRecord.endsWith("...and something else..."));
  }

  @Test
  void customPathDetection_1() {
    var path = "kubernetes://deploy/pod_a";
    assertFalse(AnaLogUtils.isLocalFilePath(path));
  }

  @Test
  void customPathDetection_2() {
    var path = "C:/Users/Anonymous/app.log";
    assertTrue(AnaLogUtils.isLocalFilePath(path));
  }

  @Test
  void customPathDetection_3() {
    var path = "docker:app.log";
    assertTrue(AnaLogUtils.isLocalFilePath(path));
  }
}