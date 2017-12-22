package tech.toparvion.analog.remote.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.integration.rmi.RmiInboundGateway;
import tech.toparvion.analog.model.config.ClusterProperties;
import tech.toparvion.analog.service.tail.GnuCoreUtilsTailSpecificsProvider;
import tech.toparvion.analog.service.tail.SolarisTailSpecificsProvider;
import tech.toparvion.analog.service.tail.TailSpecificsProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;

import static java.lang.String.format;
import static org.springframework.integration.dsl.channel.MessageChannels.direct;
import static tech.toparvion.analog.remote.RemotingConstants.*;

/**
 * Spring configuration bean that composes tracking flow on the server side.
 *
 * @author Toparvion
 * @since v0.7
 */
@Configuration
@IntegrationComponentScan
@EnableIntegrationManagement(defaultCountsEnabled = "true", defaultStatsEnabled = "true")
public class ServerConfig {
  private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

  @Bean
  public IntegrationFlow serverRegistrationRouter() {
    /* Because the number and names of cluster nodes are not fixed, it is impossible to declare separate integration
    flows for every one of them. Therefore, the flows are created separately and dynamically. But this brings another
    problem - how to address them during messages dispatching? To solve this problem the input channel of every
    integration flow is named by format "SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX + nodeName". The first one is
    a constant and the second is given as corresponding message's payload field. The following intermediate flow uses
    SpEL to extract the field value, combine it with the constant value and thus define the channel name to redirect
    the message to. */
    return IntegrationFlows.from(direct(SERVER_REGISTRATION_ROUTER__CHANNEL))
        .route(format("'%s'.concat(payload.nodeName)", SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX))
        .get();
  }

  @Bean
  public IntegrationFlow serverRmiPayloadFlow(ClusterProperties clusterProperties,
                                              RecordSender recordSender,
                                              MetaDataSender metaDataSender) {
    DirectChannel payloadRmiInChannel = direct(SERVER_RMI_PAYLOAD_IN__CHANNEL).get();
    int myPort = clusterProperties.getMyselfNode().getPort();

    RmiInboundGateway inboundRmiGateway = new RmiInboundGateway();
    inboundRmiGateway.setRequestChannel(payloadRmiInChannel);
    // inboundRmiGateway.setRegistryHost(host);// this causes application failure at startup due to 'connection refused'
    inboundRmiGateway.setRegistryPort(myPort);
    inboundRmiGateway.setExpectReply(false);    // to avoid 1 sec delay on every request/response exchange

    return IntegrationFlows
        .from(inboundRmiGateway)
        .<Object, Class<?>>   // 'Object' stands for message payload; 'Class<?>' stands for payload type
            route(this::detectPayloadClass, routerSpec -> routerSpec
                .subFlowMapping(Collection.class, flow -> flow.handle(recordSender::sendRecord))
                .subFlowMapping(FileTailingEvent.class, flow -> flow.handle(metaDataSender::sendMetaData)))
        .get();
  }

  /**
   * Since records can arrive in various forms of collection, AnaLog needs a way to generalize it. Otherwise the router
   * wouldn't be able to correctly perform the redirect because payload type router relies on trivial class name
   * comparison only. To address that issue this method detects and returns {@link Collection} type for any
   * appropriate message payload.
   * @return message payload type to base the routing on
   */
  private Class<?> detectPayloadClass(Object messagePayload) {
    return Collection.class.isAssignableFrom(messagePayload.getClass())
        ? Collection.class
        : messagePayload.getClass();
  }

  @Bean
  @Lazy(false)      // to reveal problems with tail ASAP
  public TailSpecificsProvider tailSpecificsProvider() throws Exception {
    String idfString = obtainTailIdfString();
    TailSpecificsProvider specificsProvider;
    if (GnuCoreUtilsTailSpecificsProvider.matches(idfString)) {
      specificsProvider = new GnuCoreUtilsTailSpecificsProvider();

    } else if (SolarisTailSpecificsProvider.matches(idfString)) {
      specificsProvider = new SolarisTailSpecificsProvider();

    } else {
      throw new IllegalStateException("No suitable specifics provider found for tail's idf string: " + idfString +
      "\nPlease post this message to https://github.com/Toparvion/analog/issues/new in order to support this " +
          " tail implementation in future versions of AnaLog.");
    }

    log.info("Found 'tail' program on this server and selected '{}' for it.", specificsProvider.getClass().getSimpleName());
    return specificsProvider;
  }

  /**
   * Launches {@code tail} program with {@code --version} option and reads the first line that {@code tail} prints in reply.
   * This option is supported by GNU coreutils tail implementation only but this is not an issue as other implementations
   * can also be recognized by analyzing the first output line (which can be found either in standard or error input).<p>
   * Since such a behavior is highly dependent on various implementations, this method is potential subject to change in
   * future releases.
   * @return the first line which {@code tail} program returns in reply to invocation with {@code --version} option
   * @throws Exception when {@code tail} program is absent or cannot be accessed by AnaLog
   */
  private String obtainTailIdfString() throws Exception {
    // first check whether tail is present and try to run it
    Process process;
    try {
      process = Runtime.getRuntime().exec("tail --version");
    } catch (IOException e) {
      if (e.getMessage().startsWith("Cannot run program")) {
        log.error("Failed to find 'tail' program on this server. Please check if it is correctly " +
            "installed, accessible for AnaLog and its path is included into PATH environment variable. Root cause: "
            + e.getMessage());
      }
      throw e;
    }

    // then read the first string it has printed
    String firstLine;
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      firstLine = bufferedReader.readLine();
    }
    if (firstLine != null && !"".equals(firstLine)) {
      log.debug("Obtained idf line of tail from standard input: '{}'", firstLine);
    } else {
      // tail might reply in error stream, e.g. on Solaris OS, so let's check if error input contains any data
      try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        firstLine = bufferedReader.readLine();
        log.debug("Obtained idf line of tail from error input: '{}'", firstLine);
      }
    }
    log.debug("Waiting for tail to finish...");
    process.waitFor();
    log.debug("tail has finished. Going on.");
    return firstLine;
  }

}
