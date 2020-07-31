/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.toparvion.analog.remote.agent.si;

import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import static java.lang.String.format;

/**
 * <code><pre>
 * sudo docker logs --follow --tail=0 b181bbb05d49
 * kubectl logs --follow --tail=1 deployment/devrel-restorun
 * kubectl logs --follow --tail=1 devrel-restorun-cbfc8b84f-fch4h
 * </pre></code>
 *
 * @author Gary Russell
 * @author Gavin Gray
 * @author Ali Shahbour
 * @author Vladimir Plizga
 * @since 3.0
 */
public class ProcessTailMessageProducer extends FileTailingMessageProducerSupport
        implements SchedulingAwareRunnable {

  private volatile Process process;

  private volatile String executable;

  private volatile String options = "--follow --tail=1";

  private volatile String command = "ADAPTER_NOT_INITIALIZED";

  private volatile boolean enableStatusReader = true;

  private volatile BufferedReader reader;


  public void setOptions(String options) {
    if (options == null) {
      this.options = "";
    } else {
      this.options = options;
    }
  }

  public void setExecutable(String executable) {
    this.executable = executable;
  }

  /**
   * If false, thread for capturing stderr will not be started
   * and stderr output will be ignored
   *
   * @param enableStatusReader true or false
   * @since 4.3.6
   */
  public void setEnableStatusReader(boolean enableStatusReader) {
    this.enableStatusReader = enableStatusReader;
  }

  public String getCommand() {
    return this.command;
  }

  @Override
  public String getComponentType() {
    return super.getComponentType() + " (process tail)";
  }

  @Override
  public boolean isLongLived() {
    return true;
  }

  @Override
  protected void onInit() {
    Assert.hasText(executable, "Executable cannot be empty");
    Assert.notNull(getFile(), "File cannot be null");
    super.onInit();
  }

  @Override
  protected void doStart() {
    super.doStart();
    destroyProcess();
//    this.command = "kubectl logs " + this.options + " " + target;
    try {
      this.command = format("%s %s %s", this.executable, this.options, getFile().getCanonicalPath());
    } catch (IOException e) {
      throw new MessagingException(format("Failed to start process tail producer for executable=%s and options='%s'",
              executable, this.options), e);
    }
    this.getTaskExecutor().execute(this::runExec);
  }

  @Override
  protected void doStop() {
    super.doStop();
    destroyProcess();
  }

  private void destroyProcess() {
    Process process = this.process;
    if (process != null) {
      process.destroy();
      this.process = null;
    }
  }

  /**
   * Exec the native tail process.
   */
  private void runExec() {
    this.destroyProcess();
    if (logger.isInfoEnabled()) {
      logger.info("Starting tail process with command: " + this.command);
    }
    try {
      Process process = Runtime.getRuntime().exec(this.command);
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      this.process = process;
      this.startProcessMonitor();
      if (this.enableStatusReader) {
        startStatusReader();
      }
      this.reader = reader;
      this.getTaskExecutor().execute(this);
    } catch (IOException e) {
      throw new MessagingException("Failed to exec tail command: '" + this.command + "'", e);
    }
  }


  /**
   * Runs a thread that waits for the Process result.
   */
  private void startProcessMonitor() {
    this.getTaskExecutor().execute(() -> {
      Process process = ProcessTailMessageProducer.this.process;
      if (process == null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Process destroyed before starting process monitor");
        }
        return;
      }

      int result;
      try {
        if (logger.isDebugEnabled()) {
          logger.debug("Monitoring process " + process);
        }
        result = process.waitFor();
        if (logger.isInfoEnabled()) {
          logger.info("tail process terminated with value " + result);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Interrupted - stopping adapter", e);
        stop();
      } finally {
        destroyProcess();
      }
      if (isRunning()) {
        if (logger.isInfoEnabled()) {
          logger.info("Restarting tail process in " + getMissingFileDelay() + " milliseconds");
        }
        getTaskScheduler()
                .schedule(this::runExec, new Date(System.currentTimeMillis() + getMissingFileDelay()));
      }
    });
  }

  /**
   * Runs a thread that reads stderr - on some platforms status messages
   * (file not available, rotations etc) are sent to stderr.
   */
  private void startStatusReader() {
    Process process = this.process;
    if (process == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Process destroyed before starting stderr reader");
      }
      return;
    }
    this.getTaskExecutor().execute(() -> {
      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      String statusMessage;
      if (logger.isDebugEnabled()) {
        logger.debug("Reading stderr");
      }
      try {
        while ((statusMessage = errorReader.readLine()) != null) {
          publish(statusMessage);
          if (logger.isTraceEnabled()) {
            logger.trace(statusMessage);
          }
        }
      } catch (IOException e1) {
        if (logger.isDebugEnabled()) {
          logger.debug("Exception on tail error reader", e1);
        }
      } finally {
        try {
          errorReader.close();
        } catch (IOException e2) {
          if (logger.isDebugEnabled()) {
            logger.debug("Exception while closing stderr", e2);
          }
        }
      }
    });
  }

  /**
   * Reads lines from stdout and sends in a message to the output channel.
   */
  @Override
  public void run() {
    String line;
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Reading stdout");
      }
      while ((line = this.reader.readLine()) != null) {
        this.send(line);
      }
    } catch (IOException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Exception on tail reader", e);
      }
      try {
        this.reader.close();
      } catch (IOException e1) {
        if (logger.isDebugEnabled()) {
          logger.debug("Exception while closing stdout", e);
        }
      }
      this.destroyProcess();
    }
  }

}
