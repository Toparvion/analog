package tech.toparvion.analog.remote.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tech.toparvion.analog.model.api.StyledLine;
import tech.toparvion.analog.service.RecordLevelDetector;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.mockito.Mockito.mock;

/**
 * @author Toparvion
 * @since v0.7
 */
class RecordSenderPlainTest {
  private static final Logger log = LoggerFactory.getLogger(RecordSenderPlainTest.class);
  private RecordSender sut;

  @BeforeEach
  void setUp() {
    sut = new RecordSender(mock(RecordLevelDetector.class), mock(SimpMessagingTemplate.class), mock(ColorPicker.class));
  }

  @Test
  @DisplayName("Two consequent records with different levels and with XML content")
  void mixedContent() {
    List<String> payloadAsList = new ArrayList<>();
    payloadAsList.add("2012-10-24 13:08:00,323 [http-9014-Processor23] DEBUG [LiteEngine] generateOTP");
    payloadAsList.add("doc4hash: [<payment>");
    payloadAsList.add("<amount>100.00</amount>");
    payloadAsList.add("</payment>]");
    payloadAsList.add("23.10.12 19:18:09 [TP-Processor7] TRACE cam.CamCardServiceClient SCT:13a8d8eeddd - REQ:");
    payloadAsList.add("<payee account=\"40911810100060000005\"/>");
    payloadAsList.add("<payment-info>2196946604 ");
    payloadAsList.add("Tax Free</payment-info>");
    payloadAsList.add("doc4sms: [N695: #508#]");

    List<StyledLine> records = sut.prepareFlatMessage(payloadAsList);
    log.info("\n{}", records.stream()
        .map(rec -> String.format("%7s: %s", rec.getStyle(), rec.getText()))
        .collect(joining("\n")));
  }
}
