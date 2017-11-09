package tech.toparvion.analog.util.dev;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.helper.RenameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.util.DynamicPeriodicTrigger;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Toparvion
 * @since v0.7
 */
@ManagedResource
public class LogFileGenerator implements Runnable, InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(LogFileGenerator.class);
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private final Path sourceFilePath;
  private final List<String> sourceLines;
  private final Pattern sourceTimestampPattern;
  private final Path destinationFilePath;
  private final DateTimeFormatter destinationTimestampFormatter;
  private final DynamicPeriodicTrigger trigger;
  private final int bunchSize;
  private final TaskScheduler taskScheduler;

  private CountDownLatch pause = null;

  public LogFileGenerator(String sourceFilePath,
                          String destinationFilePath,
                          String sourceTimestampPattern,
                          String destinationTimestampFormat,
                          long generationPeriod,
                          int bunchSize,
                          TaskScheduler taskScheduler) throws IOException {
    this.sourceFilePath = Paths.get(sourceFilePath);
    this.sourceLines = Files.readAllLines(this.sourceFilePath);
    this.destinationFilePath = Paths.get(destinationFilePath);
    this.sourceTimestampPattern = Pattern.compile(sourceTimestampPattern);
    this.destinationTimestampFormatter = DateTimeFormatter.ofPattern(destinationTimestampFormat);
    this.trigger = new DynamicPeriodicTrigger(generationPeriod, SECONDS);
    this.bunchSize = bunchSize;
    this.taskScheduler = taskScheduler;

    this.trigger.setFixedRate(false); // in order to prevent accumulation of unpublished records during a pause
    this.trigger.setInitialDelay(5);  // to start generation after the application is fully up and running
  }

  @Override
  public void run() {
    makePauseIfNeeded();
    log.trace("I'm Generator and I know it! Current period: {} {} ", getGenerationPeriod(),
        trigger.getTimeUnit().name().toLowerCase());
    String record = getSingleRandomRecord();
    try {
      Files.write(destinationFilePath, record.getBytes(UTF_8), CREATE, APPEND, SYNC);

    } catch (IOException e) {
      log.error("Couldn't write new records to log file.", e);
    }
  }

  private String getSingleRandomRecord() {
    int startIndex;
    String startRecord;
    do {
      startIndex = (int) (Math.random() * sourceLines.size());
      startRecord = sourceLines.get(startIndex);
    } while (!sourceTimestampPattern.matcher(startRecord).find());

    String recordsToAppend;

    if (startIndex == sourceLines.size() - 1) {
      recordsToAppend = replaceTimestamp(startRecord);

    } else {
      StringBuilder sb = new StringBuilder();
      int lastIndex = startIndex, recordsProduced = 0;
      int recordsToProduce = (int) (Math.random() * bunchSize) + 1;
      do {
        if (sb.length() > 0) sb.append(LINE_SEPARATOR);
        sb.append(replaceTimestamp(sourceLines.get(lastIndex++)));
        int i;
        for (i=lastIndex; i<sourceLines.size(); i++) {
          if (sourceTimestampPattern.matcher(sourceLines.get(i)).find()) {
            break;
          }
          sb.append(LINE_SEPARATOR)
            .append(replaceTimestamp(sourceLines.get(i)));
        }
        lastIndex = i;
      } while (lastIndex < sourceLines.size() && ++recordsProduced < recordsToProduce);
      sb.append(LINE_SEPARATOR);
      recordsToAppend = sb.toString();
      log.debug("Produced {} record(s).", recordsProduced);
      if (log.isTraceEnabled()) {
        log.trace("Produced records:\n{}", "> " + recordsToAppend.replaceAll(Pattern.quote(LINE_SEPARATOR), "\n> "));
      }
    }

    return recordsToAppend;
  }

  private String replaceTimestamp(String originalLine) {
    String currentTimestamp = destinationTimestampFormatter.format(LocalDateTime.now());
    return sourceTimestampPattern.matcher(originalLine).replaceFirst(currentTimestamp);
  }

  private void makePauseIfNeeded() {
    try {
      if (pause != null) {
        log.info("Log generation paused.");
        pause.await();
        pause = null;
        log.info("Log generation proceeded.");
      }
    } catch (InterruptedException e) {
      log.error("Interrupted while pausing.", e);
    }
  }

  @ManagedAttribute
  public long getGenerationPeriod() {
    return TimeUnit.MILLISECONDS.toSeconds(trigger.getPeriod());
  }

  @ManagedAttribute
  public void setGenerationPeriod(long generationPeriod) {
    log.info("JMX command: changing generation period from {} to {} {}.", getGenerationPeriod(), generationPeriod,
        trigger.getTimeUnit().name().toLowerCase());
    trigger.setPeriod(generationPeriod);
  }

  @ManagedAttribute(description = "Source file path")
  public String getSourceFilePath() {
    return sourceFilePath.toAbsolutePath().toString();
  }

  @ManagedAttribute(description = "Generated file path")
  public String getDestinationFilePath() {
    return destinationFilePath.toAbsolutePath().toString();
  }

  @ManagedOperation
  public synchronized String generatorPause() {
    String message;
    if (pause == null) {
      pause = new CountDownLatch(1);
      message = "Pause has started.";
    } else {
      message = "Cannot make a pause while previous one is acting.";
    }
    log.info("JMX command: {}", message);
    return message;
  }

  @ManagedOperation
  public synchronized String generatorResume() {
    String message;
    if (pause != null) {
      pause.countDown();
      message = "Pause has ended.";
    } else {
      message = "Cannot resume when no pause is acting.";
    }
    log.info("JMX command: {}", message);
    return message;
  }

  @ManagedOperation
  public String fileTruncate() throws IOException {
    Files.write(destinationFilePath, new byte[0], TRUNCATE_EXISTING);
    String message = String.format("truncated file '%s'", destinationFilePath.toAbsolutePath().toString());
    log.info("JMX command: {}.", message);
    return message;
  }

  @ManagedOperation
  public String fileDelete() throws IOException {
    Files.delete(destinationFilePath);
    String message = String.format("deleted file '%s'", destinationFilePath.toAbsolutePath().toString());
    log.info("JMX command: {}.", message);
    return message;
  }

  @ManagedOperation
  public String fileRotate() {
    try {
      RenameUtil renameUtil = new RenameUtil();
      renameUtil.setContext(new LoggerContext());
      String currentName = destinationFilePath.toAbsolutePath().toString();
      String newName = currentName + "." + DateTimeFormatter.ofPattern("HH-mm-ss").format(ZonedDateTime.now());
      renameUtil.rename(currentName, newName);
      String message = String.format("renamed (rotated) file from '%s' to '%s'", currentName, newName);
      log.info("JMX command: {}", message);
      return message;

    } catch (Exception e) {
      log.error("Failed to rotate log.", e);
      throw new RuntimeException(e.getCause().getMessage(), e);
    }
  }

  @Override
  public void afterPropertiesSet() {
    taskScheduler.schedule(this, trigger);
  }
}
