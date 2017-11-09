package tech.toparvion.analog.util.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.support.ConnectorServerFactoryBean;
import org.springframework.remoting.rmi.RmiRegistryFactoryBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.IOException;

/**
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@Profile("dev")
public class DevLogSource {
  private static final Logger log = LoggerFactory.getLogger(DevLogSource.class);

  @Value("${jmx.rmi.host:localhost}")
  private String rmiHost;

  @Value("${jmx.rmi.port:1099}")
  private Integer rmiPort;

  @Bean
  public RmiRegistryFactoryBean rmiRegistry() {
    final RmiRegistryFactoryBean rmiRegistryFactoryBean = new RmiRegistryFactoryBean();
    rmiRegistryFactoryBean.setPort(rmiPort);
    rmiRegistryFactoryBean.setAlwaysCreate(true);
    return rmiRegistryFactoryBean;
  }

  @Bean
  @DependsOn("rmiRegistry")
  public ConnectorServerFactoryBean connectorServerFactoryBean() throws Exception {
    final ConnectorServerFactoryBean connectorServerFactoryBean = new ConnectorServerFactoryBean();
    connectorServerFactoryBean.setObjectName("connector:name=rmi");
    connectorServerFactoryBean.setServiceUrl(String.format("service:jmx:rmi://%s:%s/jndi/rmi://%s:%s/jmxrmi",
        rmiHost, rmiPort, rmiHost, rmiPort));
    return connectorServerFactoryBean;
  }

  @Bean
  public ThreadPoolTaskScheduler logFileGeneratorTaskScheduler() {
    ThreadPoolTaskScheduler logFileGeneratorTaskScheduler = new ThreadPoolTaskScheduler();
    logFileGeneratorTaskScheduler.setThreadNamePrefix("logFileGeneratorTaskScheduler-");
    logFileGeneratorTaskScheduler.setPoolSize(3);
    return logFileGeneratorTaskScheduler;
  }

  @Bean
  @Profile("genLog1")
  public LogFileGenerator generatedLogBankplus() throws IOException {
    log.info("DEV mode: starting log generator 'bankplus'...");
    return new LogFileGenerator(
        "log-samples/source/bankplus-source.log",
        "log-samples/generated/bankplus.log",
        "^\\d{2}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}",
        "dd.MM.uu HH:mm:ss,SSS",
        3,
        3,
        logFileGeneratorTaskScheduler());
  }

  @Bean
  @Profile("genLog2")
  public LogFileGenerator generatedLogCore() throws IOException {
    log.info("DEV mode: starting log generator 'core'...");
    return new LogFileGenerator(
        "log-samples/source/core-source.log",
        "log-samples/generated/core.log",
        "\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}\\:\\d{2}\\:\\d{2}\\,\\d{3}",  // 2015-10-30 10:59:09,533
        "uuuu-MM-dd HH:mm:ss,SSS",                                  // 2015-10-30 10:59:09,533
        2,
        2,
        logFileGeneratorTaskScheduler());
  }

}
