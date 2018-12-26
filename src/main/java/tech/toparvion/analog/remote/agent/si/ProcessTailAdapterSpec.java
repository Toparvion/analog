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

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.dsl.MessageProducerSpec;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;

import java.io.File;

/**
 * A {@link MessageProducerSpec} for file tailing adapters.
 *
 * @author Artem Bilan
 * @author Vladimir Plizga
 * @since 5.0
 */
public class ProcessTailAdapterSpec extends MessageProducerSpec<ProcessTailAdapterSpec, FileTailingMessageProducerSupport> {

  private final ProcessTailInboundChannelAdapterFactoryBean factoryBean = new ProcessTailInboundChannelAdapterFactoryBean();

  private MessageChannel outputChannel;

  private MessageChannel errorChannel;

  public ProcessTailAdapterSpec() {
    super(null);
    this.factoryBean.setBeanFactory(new DefaultListableBeanFactory());
  }

  public ProcessTailAdapterSpec executable(String executable) {
    Assert.hasText(executable, "Executable must not be empty");
    this.factoryBean.setExecutbale(executable);
    return _this();
  }

  public ProcessTailAdapterSpec file(File file) {
 		Assert.notNull(file, "'file' cannot be null");
 		this.factoryBean.setFile(file);
 		return _this();
 	}

  /**
   * Specify the options string for native {@code tail} command.
   *
   * @param nativeOptions the nativeOptions.
   * @return the spec.
   * @see org.springframework.integration.file.tail.OSDelegatingFileTailingMessageProducer#setOptions(String)
   */
  public ProcessTailAdapterSpec nativeOptions(String nativeOptions) {
    this.factoryBean.setNativeOptions(nativeOptions);
    return _this();
  }


  /**
   * This field control the stderr events.
   *
   * @param enableStatusReader boolean to enable or disable events from stderr.
   * @return the spec
   */
  public ProcessTailAdapterSpec enableStatusReader(boolean enableStatusReader) {
    this.factoryBean.setEnableStatusReader(enableStatusReader);
    return _this();
  }

  /**
   * Specify the idle interval before start sending idle events.
   *
   * @param idleEventInterval interval in ms for the event idle time.
   * @return the spec.
   */
  public ProcessTailAdapterSpec idleEventInterval(long idleEventInterval) {
    this.factoryBean.setIdleEventInterval(idleEventInterval);
    return _this();
  }

  /**
   * Configure a task executor. Defaults to a
   * {@link org.springframework.core.task.SimpleAsyncTaskExecutor}.
   *
   * @param taskExecutor the taskExecutor.
   * @return the spec.
   */
  public ProcessTailAdapterSpec taskExecutor(TaskExecutor taskExecutor) {
    this.factoryBean.setTaskExecutor(taskExecutor);
    return _this();
  }

  /**
   * Set a task scheduler - defaults to the integration 'taskScheduler'.
   *
   * @param taskScheduler the taskScheduler.
   * @return the spec.
   */
  public ProcessTailAdapterSpec taskScheduler(TaskScheduler taskScheduler) {
    this.factoryBean.setTaskScheduler(taskScheduler);
    return _this();
  }

  /**
   * The delay in milliseconds between attempts to tail a non-existent file,
   * or between attempts to execute a process if it fails for any reason.
   *
   * @param fileDelay the fileDelay.
   * @return the spec.
   * @see FileTailingMessageProducerSupport#setTailAttemptsDelay(long)
   */
  public ProcessTailAdapterSpec fileDelay(long fileDelay) {
    this.factoryBean.setFileDelay(fileDelay);
    return _this();
  }

  @Override
  public ProcessTailAdapterSpec id(String id) {
    this.factoryBean.setBeanName(id);
    return _this();
  }

  @Override
  public ProcessTailAdapterSpec phase(int phase) {
    this.factoryBean.setPhase(phase);
    return _this();
  }

  @Override
  public ProcessTailAdapterSpec autoStartup(boolean autoStartup) {
    this.factoryBean.setAutoStartup(autoStartup);
    return _this();
  }

  @Override
  public ProcessTailAdapterSpec outputChannel(MessageChannel outputChannel) {
    this.outputChannel = outputChannel;
    return _this();
  }

  @Override
  public ProcessTailAdapterSpec errorChannel(MessageChannel errorChannel) {
    this.errorChannel = errorChannel;
    return _this();
  }

  @Override
  protected FileTailingMessageProducerSupport doGet() {
    if (this.outputChannel == null) {
      this.factoryBean.setOutputChannel(new NullChannel());
    }
    FileTailingMessageProducerSupport tailingMessageProducerSupport = null;
    try {
      this.factoryBean.afterPropertiesSet();
      tailingMessageProducerSupport = this.factoryBean.getObject();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (this.errorChannel != null) {
      tailingMessageProducerSupport.setErrorChannel(this.errorChannel);
    }
    tailingMessageProducerSupport.setOutputChannel(this.outputChannel);
    return tailingMessageProducerSupport;
  }

}
