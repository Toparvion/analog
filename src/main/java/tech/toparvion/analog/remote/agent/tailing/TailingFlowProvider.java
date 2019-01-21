package tech.toparvion.analog.remote.agent.tailing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageProducerSpec;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext.IntegrationFlowRegistration;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.config.entry.LogPath;
import tech.toparvion.analog.remote.agent.misc.CorrelationIdHeaderEnricher;
import tech.toparvion.analog.remote.agent.misc.SequenceNumberHeaderEnricher;
import tech.toparvion.analog.remote.agent.si.ContainerTargetFile;
import tech.toparvion.analog.remote.agent.si.ProcessTailAdapterSpec;
import tech.toparvion.analog.service.RecordLevelDetector;
import tech.toparvion.analog.service.tail.TailSpecificsProvider;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import static java.lang.String.format;
import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.integration.dsl.MessageChannels.publishSubscribe;
import static org.springframework.integration.dsl.MessageChannels.queue;
import static org.springframework.integration.file.dsl.Files.tailAdapter;
import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.remote.agent.AgentConstants.TAIL_FLOW_PREFIX;
import static tech.toparvion.analog.remote.agent.AgentConstants.TAIL_OUTPUT_CHANNEL_PREFIX;
import static tech.toparvion.analog.util.PathUtils.CUSTOM_SCHEMA_SEPARATOR;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class TailingFlowProvider {
  private static final Logger log = LoggerFactory.getLogger(TailingFlowProvider.class);
  private static final String TAIL_PROCESS_ADAPTER_PREFIX = "tailProcess_";

  private final TimestampExtractor timestampExtractor;
  private final TailSpecificsProvider tailSpecificsProvider;
  private final RecordLevelDetector recordLevelDetector;
  private final IntegrationFlowContext flowContext;
  private final int groupSizeThreshold;
  private final int groupTimeoutMs;
  private final String thisNodeName;
  private final boolean useDockerSudo;

  @Autowired
  public TailingFlowProvider(TimestampExtractor timestampExtractor,
                             TailSpecificsProvider tailSpecificsProvider,
                             RecordLevelDetector recordLevelDetector,
                             IntegrationFlowContext flowContext,
                             @Value("${tracking.group.sizeThreshold:500}") int groupSizeThreshold,
                             @Value("${tracking.group.timeoutMs:500}") int groupTimeoutMs,
                             @Value("${nodes.this.name}") String thisNodeName,
                             @Value("${adapters.docker.useSudo:true}") boolean useDockerSudo) {
    this.timestampExtractor = timestampExtractor;
    this.tailSpecificsProvider = tailSpecificsProvider;
    this.recordLevelDetector = recordLevelDetector;
    this.flowContext = flowContext;
    this.groupSizeThreshold = groupSizeThreshold;
    this.groupTimeoutMs = groupTimeoutMs;
    this.thisNodeName = thisNodeName;
    this.useDockerSudo = useDockerSudo;
  }

  /**
   * The core method for building AnaLog dynamic behavior (for grouping logs).
   * Creates and returns a grouping integration flow for watching given log. No duplicate flow checking is done inside.
   * @param logPath address data of log to tail
   * @param isTailNeeded should 'tail' include last several lines of the log?
   * @return a new tailing flow
   */
  public IntegrationFlow provideGroupFlow(LogPath logPath, boolean isTailNeeded) {
    // each group flow must have its own instance of correlationProvider as it is stateful and not thread-safe
    CorrelationIdHeaderEnricher correlationProvider = new CorrelationIdHeaderEnricher();
    // each group flow must have its own instance of sequenceProvider as it is stateful and not thread-safe
    SequenceNumberHeaderEnricher sequenceProvider = new SequenceNumberHeaderEnricher();

    /* When dealing with log message groups, some messages have to be resent to aggregator. Since the aggregator is
    capable of processing single message at a moment only, it is prepended with a queue channel that stores the incoming
    messages (including those to resend). This approach produces quite subtle situation - if the queue
    happens to be filled with some message(s) before another message arrives there to be resent, the order of
    messages can be corrupted as the early arrived messages must leave the queue early as well (since the queue is of
    FIFO discipline by default). To avoid this, the queue is created as priority one. The priority is specified as
    simple sequence number (much like the one built in FileSplitter) and provided by dedicated header enricher. */
    //noinspection ConstantConditions     // null value for the header is prevented by message composing logic
    PriorityBlockingQueue<Message<?>> queue = new PriorityBlockingQueue<>(100,
        Comparator.comparingLong(message -> message.getHeaders().get(SEQUENCE_NUMBER__HEADER, Long.class)));
    MessageChannel preAggregatorQueueChannel = queue(queue).get();

    GroupAggregatorConfigurer recordAggregatorConfigurer
        = new GroupAggregatorConfigurer(preAggregatorQueueChannel, groupSizeThreshold, groupTimeoutMs);

    String tailFlowOutChannelName = findOrCreateTailFlow(logPath, false, isTailNeeded);

    return IntegrationFlows
        .from(tailFlowOutChannelName)
        .enrichHeaders(e -> e.headerFunction(LOG_TIMESTAMP_VALUE__HEADER, timestampExtractor::extractTimestamp))
        .enrichHeaders(e -> e.headerFunction(CORRELATION_ID, correlationProvider::obtainCorrelationId))
        .enrichHeaders(e -> e.headerFunction(RECORD_LEVEL__HEADER, this::detectRecordLevel))
        .enrichHeaders(e -> e.headerFunction(SEQUENCE_NUMBER__HEADER, sequenceProvider::assignSequenceNumber))
        .channel(preAggregatorQueueChannel)
        .aggregate(recordAggregatorConfigurer::configure)
        .channel(publishSubscribe())
        .get();
  }

  /**
   * The core method for building AnaLog dynamic behavior (for flat logs).
   * Creates and returns a flat integration flow for watching given log. No duplicate flow checking is done inside.
   * @param logPath an entity (file/container/pod) to target the tailing to
   * @param isTailNeeded should 'tail' include last several lines of the log?
   * @return a new tailing flow
   */
  public IntegrationFlow provideFlatFlow(LogPath logPath, boolean isTailNeeded) {
    String tailFlowOutChannelName = findOrCreateTailFlow(logPath, true, isTailNeeded);
    return IntegrationFlows
        .from(tailFlowOutChannelName)
        .aggregate(aggregatorSpec -> aggregatorSpec
            .correlationStrategy(message -> BigDecimal.ONE)
            .releaseStrategy(group -> group.size() > groupSizeThreshold)
            .groupTimeout(groupTimeoutMs)
            .expireGroupsUponTimeout(true)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true))
        .channel(publishSubscribe())
        .get();
  }

  /**
   * Checks whether tail flow for given log already exists and, if not, creates it. Either way composes name of the
   * flow's output pub-sub channel and returns it as result.
   * @param logPath data for building the tail process
   * @param isTrackingFlat is corresponding tracking flat (not grouping)
   * @param isTailNeeded whether previous lines of log file are required or not
   * @return bean name of the flow's output channel to subscribe to
   * @implNote the method is declared {@code synchronized} in order to prevent double flow registration in case of
   * simultaneous requests from clients
   */
  private synchronized String findOrCreateTailFlow(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    // tail output channel name doesn't depend on whether the flow already exists or not, so we can define it in advance
    String fullPath = logPath.getFullPath();
    String tailOutputChannelName = TAIL_OUTPUT_CHANNEL_PREFIX + fullPath;
    // to prevent double tailing let's check if such tail flow already exists
    IntegrationFlowRegistration tailFlowRegistration = flowContext.getRegistrationById(TAIL_FLOW_PREFIX + fullPath);
    if (tailFlowRegistration == null) {
      log.debug("No tail flow found for log '{}'. Will create a new one...", fullPath);
      // first declare the tail flow alongside with its output channel (to subscribe to some time later)
      StandardIntegrationFlow tailFlow = IntegrationFlows
          .from(findAppropriateAdapter(logPath, isTrackingFlat, isTailNeeded))
          .channel(publishSubscribe(tailOutputChannelName))
          .get();
      // then register it within the app context
      tailFlowRegistration = flowContext.registration(tailFlow)
          .id(TAIL_FLOW_PREFIX + fullPath)
          .autoStartup(false)   // to prevent the flow from premature sending log lines to uninitialized tracking flow
          .register();
      log.info("New tail flow has been registered with id='{}'.", tailFlowRegistration.getId());

    } else {
      // we suppose that tail flow is structurally immutable so that the existence of a flow guarantees the existence
      // of its output channel with corresponding name
      log.debug("Found existing tail flow with output channel '{}'. Will reuse it.", tailOutputChannelName);
    }
    return tailOutputChannelName;
  }

  private MessageProducerSpec<?, ?> findAppropriateAdapter(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    switch (logPath.getType()) {
      case DOCKER:
        return newTailAdapter4Docker(logPath, isTrackingFlat, isTailNeeded);

      case KUBERNETES:
      case K8S:
        return newTailAdapter4Kubernetes(logPath, isTrackingFlat, isTailNeeded);

      case NODE:
        Assert.isTrue(thisNodeName.equals(logPath.getNode()),
                      format("request for node '%s' has come to node '%s'", logPath.getNode(), thisNodeName));
        // no break needed
      case LOCAL_FILE:
        return newTailAdapter4File(logPath, isTrackingFlat, isTailNeeded);

      default:
        throw new IllegalArgumentException("Unsupported type of logPath: " + logPath);
    }
  }

  private MessageProducerSpec<?, ?> newTailAdapter4Docker(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    int tailLength = isTailNeeded
            ? isTrackingFlat ? 45 : 20
            : 0;
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    String dockerLogsOptions = format("--follow --tail=%d", tailLength);
    String fullPrefix = logPath.getType().getPrefix() + CUSTOM_SCHEMA_SEPARATOR;
    String command = (useDockerSudo ? "sudo " : "")
                + "docker logs";
    return new ProcessTailAdapterSpec()
        .executable(command)
        .file(new ContainerTargetFile(fullPrefix, logPath.getTarget()))
        .id(adapterId)
        .nativeOptions(dockerLogsOptions)
        .fileDelay(5000)
        .enableStatusReader(true);
  }

  private MessageProducerSpec<?, ?> newTailAdapter4Kubernetes(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    int tailLength = isTailNeeded
        ? isTrackingFlat ? 45 : 20
        : 1;                                // why 1 see in https://github.com/kubernetes/kubernetes/issues/35335
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    StringBuilder optsBuilder = new StringBuilder();
    optsBuilder.append(format("--follow --tail=%d", tailLength));
    String resource = null;
    String[] tokens = logPath.getTarget().split("/");
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      switch (token.toLowerCase()) {
        case "namespace":
          String namespace = tokens[i + 1];
          optsBuilder.append(" --namespace=").append(namespace);
          i++;
          break;
        case "container":
        case "c":
          String container = tokens[i + 1];
          optsBuilder.append(" --container=").append(container);
          i++;
          break;
        default:
          String newResource;// i.e. if index (i+1) exists in tokens array
          if ((i + 1) <= (tokens.length - 1)) {
            newResource = String.format("%s/%s", tokens[i], tokens[i + 1]);
            i++;
          } else {
            newResource = tokens[i];
          }
          if (hasText(resource)) {
            log.warn("Parsed resource '{}' will be overwritten with '{}'.", resource, newResource);
          }
          resource = newResource;
      }
    }
    Assert.hasText(resource, "No resource specified in path " + logPath.getTarget());
    String k8sLogsOptions = optsBuilder.toString();
    log.debug("Target '{}' is converted into resource '{}' and options: {}", logPath.getTarget(), resource, k8sLogsOptions);

    String fullPrefix = logPath.getType().getPrefix() + CUSTOM_SCHEMA_SEPARATOR;
    return new ProcessTailAdapterSpec()
        .executable("kubectl logs")
        .file(new ContainerTargetFile(fullPrefix, resource))
        .id(adapterId)
        .nativeOptions(k8sLogsOptions)
        .fileDelay(5000)
        .enableStatusReader(true);
  }

  private MessageProducerSpec<?, ?> newTailAdapter4File(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    String tailNativeOptions = isTrackingFlat
        ? tailSpecificsProvider.getFlatTailNativeOptions(isTailNeeded)
        : tailSpecificsProvider.getGroupTailNativeOptions(isTailNeeded);
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    return tailAdapter(new File(logPath.getFullPath()))
        .id(adapterId)
        .nativeOptions(tailNativeOptions)
        .fileDelay(tailSpecificsProvider.getAttemptsDelay())
        .enableStatusReader(true);
  }

  @Nullable
  private String detectRecordLevel(Message<String> recordMessage) {
    if (!recordMessage.getHeaders().containsKey(LOG_TIMESTAMP_VALUE__HEADER)) {
      return null;
    }
    return recordLevelDetector.detectLevel(recordMessage.getPayload())
                              .orElse(PLAIN_RECORD_LEVEL_NAME);
  }

}
