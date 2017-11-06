package tech.toparvion.analog.util.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.integration.dsl.Pollers.fixedDelay;
import static org.springframework.integration.file.dsl.Files.outboundAdapter;
import static org.springframework.integration.file.support.FileExistsMode.APPEND;

/**
 * @author Toparvion
 */
@Configuration
@Profile("dev")
public class DevLogSource {
  private static final Logger log = LoggerFactory.getLogger(DevLogSource.class);

  private static final Pattern LOG_RECORD_START_REGEX = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.uu HH:mm:ss,SSS");
  private static final int FETCH_PERIOD_SEC = 4;

  private final List<String> records;

  public DevLogSource() throws IOException {
    records = Files.readAllLines(Paths.get("log-samples/cluster/source/bankplus-source.log"));
//    Files.deleteIfExists(Paths.get("log-samples/cluster/local/growing.log"));
  }

  private Message<Object> getSingleRandomRecord() {
    int startIndex;
    String startRecord;
    do {
      startIndex = (int) (Math.random() * records.size());
      startRecord = records.get(startIndex);
    } while (!LOG_RECORD_START_REGEX.matcher(startRecord).find());

    String recordsToAppend;

    if (startIndex == records.size() - 1) {
      recordsToAppend = replaceTimestamp(startRecord);

    } else {
      StringBuilder sb = new StringBuilder();
      int lastIndex = startIndex, recordsProduced = 0;
      int recordsToProduce = (int) (Math.random() * 3d) + 1;
      do {
        if (sb.length() > 0) sb.append('\n');
        sb.append(replaceTimestamp(records.get(lastIndex++)));
        int i;
        for (i=lastIndex; i<records.size(); i++) {
          if (LOG_RECORD_START_REGEX.matcher(records.get(i)).find()) {
            break;
          }
          sb.append('\n').append(replaceTimestamp(records.get(i)));
        }
        lastIndex = i;
      } while (lastIndex < records.size() && ++recordsProduced < recordsToProduce);
      recordsToAppend = sb.toString();
      log.debug("Выпущено {} записей:\n{}", recordsProduced, "> " + recordsToAppend.replaceAll("\\n", "\n> "));
    }

    return MessageBuilder
            .<Object>withPayload(recordsToAppend)
            .build();
  }

  private String replaceTimestamp(String originalLine) {
    String currentTimestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
    return LOG_RECORD_START_REGEX.matcher(originalLine).replaceFirst(currentTimestamp);
  }

  @Bean
  public IntegrationFlow sampleLogFillingFlow() {
    return IntegrationFlows
        .from(this::getSingleRandomRecord,
            conf -> conf
                .poller(fixedDelay(FETCH_PERIOD_SEC, SECONDS))
                .autoStartup(true))
        .handle(outboundAdapter(new File("log-samples/cluster"))
            .fileNameGenerator(name -> "composite.log")
            .fileExistsMode(APPEND)
            .appendNewLine(true))
        .get();
  }

}
