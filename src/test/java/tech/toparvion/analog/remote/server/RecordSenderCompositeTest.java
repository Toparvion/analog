package tech.toparvion.analog.remote.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tech.toparvion.analog.model.api.StyledLine;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @author Toparvion
 * @since v0.7
 */
class RecordSenderCompositeTest {
  private static final Logger log = LoggerFactory.getLogger(RecordSenderCompositeTest.class);
  private RecordSender sut;

  @BeforeEach
  void setUp() {
    sut = new RecordSender(mock(SimpMessagingTemplate.class), mock(ColorPicker.class));
  }

  @Test
  @DisplayName("Empty payload is handled immediately")
  void emptyPayload() {
    List<String> payloadAsList = emptyList();

    List<StyledLine> styledLines = sut.prepareCompositeRecords(payloadAsList, null);
    assertThat(styledLines, is(empty()));
  }

  @Test
  @DisplayName("XML in the very first line is considered error")
  void invalidFirstLine() {
    List<String> payload = new ArrayList<>();
    payload.add("<payment><amount>100.00</amount></payment>");
    assertThrows(IllegalStateException.class,
        () -> sut.prepareCompositeRecords(payload, null));
  }

  @Test
  @DisplayName("A text preceding single-line-XML is recognized correctly")
  void preXmlTextIsFormattedCorrectly() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] - generateOTP: " +
                      "<payment><amount>100.00</amount></payment>");

    List<StyledLine> records = sut.prepareCompositeRecords(payloadAsList, "DEBUG");
    log.trace("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));

    assertThat(records, hasSize(2));
    assertEquals("DEBUG", records.get(0).getStyle());
    assertEquals("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] - generateOTP: ",
        records.get(0).getText());
    assertEquals("XML", records.get(1).getStyle());
    assertEquals("&lt;payment&gt;\n" +
        "  &lt;amount&gt;100.00&lt;/amount&gt;\n" +
        "&lt;/payment&gt;\n", records.get(1).getText());
  }

  @Test
  @DisplayName("A text after single-line-XML is correctly appended to records")
  void postXmlTextIsFormattedCorrectly() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] - generateOTP: " +
        "doc4hash: [<payment><amount>100.00</amount></payment>], " +
        "docHash: com.tfc.web.mdse.model.DocHash@135ecf1");

    List<StyledLine> records = sut.prepareCompositeRecords(payloadAsList, "DEBUG");
    log.info("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));

    assertThat(records, hasSize(3));
    assertEquals("DEBUG", records.get(0).getStyle());
    assertEquals("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] - generateOTP: doc4hash: [",
        records.get(0).getText());
    assertEquals("XML", records.get(1).getStyle());
    assertEquals("&lt;payment&gt;\n" +
        "  &lt;amount&gt;100.00&lt;/amount&gt;\n" +
        "&lt;/payment&gt;\n", records.get(1).getText());
    assertEquals("PLAIN", records.get(2).getStyle());
    assertEquals("], docHash: com.tfc.web.mdse.model.DocHash@135ecf1", records.get(2).getText());
  }

  @Test
  @DisplayName("Multi line XML document with no non-XML ending")
  void multiLinePureXml() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP");
    payloadAsList.add("<payment>");
    payloadAsList.add("<amount>100.00</amount>");
    payloadAsList.add("<payee account=\"40911810100060000005\"/>");
    payloadAsList.add("<payment-info>2196946604 ");
    payloadAsList.add("Tax Free</payment-info>");
    payloadAsList.add("</payment>");

    List<StyledLine> records = sut.prepareCompositeRecords(payloadAsList, "DEBUG");
    log.info("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));

    assertThat(records, hasSize(2));
    assertEquals("DEBUG", records.get(0).getStyle());
    assertEquals("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP",
        records.get(0).getText());
    assertEquals("XML", records.get(1).getStyle());
    assertEquals("&lt;payment&gt;\n" +
        "  &lt;amount&gt;100.00&lt;/amount&gt;\n" +
        "  &lt;payee account=&quot;40911810100060000005&quot; /&gt;\n" +
        "  &lt;payment-info&gt;2196946604 Tax Free&lt;/payment-info&gt;\n" +
        "&lt;/payment&gt;\n", records.get(1).getText());
  }

  @Test
  @DisplayName("Multi line XML surrounded with non-xml text")
  void mix() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP");
    payloadAsList.add("doc4hash: [<payment>");
    payloadAsList.add("<amount>100.00</amount>");
    payloadAsList.add("<payee account=\"40911810100060000005\"/>");
    payloadAsList.add("<payment-info>2196946604 ");
    payloadAsList.add("Tax Free</payment-info>");
    payloadAsList.add("</payment>]");
    payloadAsList.add("docHash: ru.cft.web.mDSE.model.DocHash@135ecf1");

    List<StyledLine> records = sut.prepareCompositeRecords(payloadAsList, "DEBUG");
    log.info("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));

    assertThat(records, hasSize(5));
    assertEquals("DEBUG", records.get(0).getStyle());
    assertEquals("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP",
        records.get(0).getText());
    assertEquals("PLAIN", records.get(1).getStyle());
    assertEquals("doc4hash: [", records.get(1).getText());
    assertEquals("XML", records.get(2).getStyle());
    assertEquals("&lt;payment&gt;\n" +
        "  &lt;amount&gt;100.00&lt;/amount&gt;\n" +
        "  &lt;payee account=&quot;40911810100060000005&quot; /&gt;\n" +
        "  &lt;payment-info&gt;2196946604 Tax Free&lt;/payment-info&gt;\n" +
        "&lt;/payment&gt;\n", records.get(2).getText());
    assertEquals("PLAIN", records.get(3).getStyle());
    assertEquals("]", records.get(3).getText());
    assertEquals("PLAIN", records.get(4).getStyle());
    assertEquals("docHash: ru.cft.web.mDSE.model.DocHash@135ecf1", records.get(4).getText());
  }

  @Test
  @DisplayName("Multiple XML documents mixed with plain text lines")
  void multipleXmlDocuments() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP");
    payloadAsList.add("doc4hash: [<payment>");
    payloadAsList.add("<amount>100.00</amount>");
    payloadAsList.add("</payment>]");
    payloadAsList.add("docHash: ru.cft.web.mDSE.model.DocHash@135ecf1");
    payloadAsList.add("<payee account=\"40911810100060000005\"/>");
    payloadAsList.add("<payment-info>2196946604 ");
    payloadAsList.add("Tax Free</payment-info>");
    payloadAsList.add("doc4sms: [N695: #508#]");

    List<StyledLine> records = sut.prepareCompositeRecords(payloadAsList, "DEBUG");
    log.info("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));

    assertThat(records, hasSize(8));
    assertEquals("XML", records.get(2).getStyle());
    assertEquals("&lt;payment&gt;\n" +
        "  &lt;amount&gt;100.00&lt;/amount&gt;\n" +
        "&lt;/payment&gt;\n", records.get(2).getText());
    assertEquals("PLAIN", records.get(5).getStyle());
    assertEquals("&lt;payee account=&quot;40911810100060000005&quot;/&gt;", records.get(5).getText());
    assertEquals("XML", records.get(6).getStyle());
    assertEquals("&lt;payment-info&gt;2196946604 Tax Free&lt;/payment-info&gt;\n", records.get(6).getText());
  }

}