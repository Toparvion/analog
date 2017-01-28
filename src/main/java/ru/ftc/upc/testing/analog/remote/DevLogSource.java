package ru.ftc.upc.testing.analog.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.integration.dsl.Pollers.fixedDelay;
import static org.springframework.integration.file.dsl.Files.outboundAdapter;

/**
 * @author Toparvion
 */
@Configuration
@Profile("dev")
public class DevLogSource {
  private static final Logger log = LoggerFactory.getLogger(DevLogSource.class);

  private final List<String> records;

  public DevLogSource() throws IOException {
    records = Files.readAllLines(Paths.get("log-samples/cluster/local/static-source.log"));
//    Files.deleteIfExists(Paths.get("log-samples/cluster/local/growing.log"));
  }

  private Message<Object> getRandomRecord() {
    return MessageBuilder
            .<Object>withPayload(records.get((int) (Math.random() * records.size())))
            .build();
  }

  @Bean
  public IntegrationFlow sampleLogFillingFlow() {
    return IntegrationFlows
        .from(this::getRandomRecord,
            conf -> conf
                .poller(fixedDelay(15, SECONDS))
                .autoStartup(true))
        .handle(outboundAdapter(new File("log-samples/cluster/local"))
            .fileNameGenerator(name -> "growing.log")
            .fileExistsMode(FileExistsMode.APPEND)
            .appendNewLine(true))
        .get();
  }

}
