package tech.toparvion.analog.remote.agent.tailing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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
import tech.toparvion.analog.model.config.adapters.GeneralAdapterParams;
import tech.toparvion.analog.model.config.adapters.TrackingProperties;
import tech.toparvion.analog.model.config.entry.LogPath;
import tech.toparvion.analog.remote.agent.enrich.CorrelationIdHeaderEnricher;
import tech.toparvion.analog.remote.agent.enrich.SequenceNumberHeaderEnricher;
import tech.toparvion.analog.remote.agent.origin.adapt.DockerOriginAdapter;
import tech.toparvion.analog.remote.agent.origin.adapt.FileOriginAdapter;
import tech.toparvion.analog.remote.agent.origin.adapt.KubernetesOriginAdapter;
import tech.toparvion.analog.remote.agent.origin.restrict.FileAccessGuard;
import tech.toparvion.analog.remote.agent.si.ContainerTargetFile;
import tech.toparvion.analog.remote.agent.si.ProcessTailAdapterSpec;
import tech.toparvion.analog.service.RecordLevelDetector;
import tech.toparvion.analog.util.PathUtils;
import tech.toparvion.analog.util.timestamp.TimestampExtractor;

import java.io.File;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import static java.lang.String.format;
import static org.springframework.integration.IntegrationMessageHeaderAccessor.CORRELATION_ID;
import static org.springframework.integration.dsl.MessageChannels.publishSubscribe;
import static org.springframework.integration.dsl.MessageChannels.queue;
import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.remote.RemotingConstants.*;
import static tech.toparvion.analog.remote.agent.AgentConstants.*;
import static tech.toparvion.analog.util.PathUtils.CUSTOM_SCHEMA_SEPARATOR;

/**
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class TailingFlowProvider {
  private static final Logger log = LoggerFactory.getLogger(TailingFlowProvider.class);

  private final TimestampExtractor timestampExtractor;
  private final RecordLevelDetector recordLevelDetector;
  private final IntegrationFlowContext flowContext;
  private final TrackingProperties trackingProperties;
  private final FileAccessGuard fileAccessGuard;
  private final ApplicationContext appContext;
  private final String thisNodeName;

  @Autowired
  public TailingFlowProvider(TimestampExtractor timestampExtractor,
                             RecordLevelDetector recordLevelDetector,
                             IntegrationFlowContext flowContext,
                             TrackingProperties trackingProperties,
                             FileAccessGuard fileAccessGuard,
                             ApplicationContext appContext,
                             @Value("${nodes.this.name}") String thisNodeName) {
    this.timestampExtractor = timestampExtractor;
    this.recordLevelDetector = recordLevelDetector;
    this.flowContext = flowContext;
    this.trackingProperties = trackingProperties;
    this.fileAccessGuard = fileAccessGuard;
    this.appContext = appContext;
    this.thisNodeName = thisNodeName;
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

    int groupSizeThreshold = trackingProperties.getGrouping().getSizeThreshold();
    long groupTimeout = trackingProperties.getGrouping().getTimeout().toMillis();
    GroupAggregatorConfigurer recordAggregatorConfigurer
        = new GroupAggregatorConfigurer(preAggregatorQueueChannel, groupSizeThreshold, groupTimeout);

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
    int groupSizeThreshold = trackingProperties.getGrouping().getSizeThreshold();
    long groupTimeout = trackingProperties.getGrouping().getTimeout().toMillis();
    return IntegrationFlows
        .from(tailFlowOutChannelName)
        .aggregate(aggregatorSpec -> aggregatorSpec
            .correlationStrategy(message -> BigDecimal.ONE)
            .releaseStrategy(group -> group.size() > groupSizeThreshold)
            .groupTimeout(groupTimeout)
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
                      format("request aimed to node '%s' has come to different node '%s'", logPath.getNode(), thisNodeName));
        // no break needed
      case LOCAL_FILE:
        return newTailAdapter4File(logPath, isTrackingFlat, isTailNeeded);

      case COMPOSITE:
        throw new IllegalStateException("Single adapter cannot be applied to composite log: " + logPath);

      default:
        throw new IllegalArgumentException("Unsupported type of logPath: " + logPath);
    }
  }

  private MessageProducerSpec<?, ?> newTailAdapter4File(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    FileOriginAdapter fileOriginAdapter = appContext.getBean(FileOriginAdapter.class); // (!) this will init the adapter firstly!
    GeneralAdapterParams adapterParams = fileOriginAdapter.adapterParams();
    String followCommand = adapterParams.getFollowCommand();
    int tailSize = isTailNeeded
            ? isTrackingFlat
              ? trackingProperties.getTailSize().getFlat()
              : trackingProperties.getTailSize().getGroup()
            : 0;
    String nativeOptions = MessageFormat.format(followCommand, tailSize);
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    String executable = adapterParams.getExecutable();
    log.debug("Starting file tracking with executable '{}' and options '{}'...", executable, nativeOptions);
    String localPath = PathUtils.extractLocalPath(logPath);
    // the following call will throw AccessControlException in case of violation
    fileAccessGuard.checkAccess(localPath);
    return new ProcessTailAdapterSpec()
            .executable(executable)
            .file(new File(localPath))
            .id(adapterId)
            .nativeOptions(nativeOptions)
            .fileDelay(trackingProperties.getRetryDelay().toMillis())
            .enableStatusReader(true);
  }

  private MessageProducerSpec<?, ?> newTailAdapter4Docker(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    DockerOriginAdapter dockerOriginAdapter = appContext.getBean(DockerOriginAdapter.class); // (!) this will init the adapter firstly!
    GeneralAdapterParams adapterParams = dockerOriginAdapter.adapterParams();
    String followCommand = adapterParams.getFollowCommand();
    int tailSize = isTailNeeded
            ? isTrackingFlat
              ? trackingProperties.getTailSize().getFlat()
              : trackingProperties.getTailSize().getGroup()
            : 0;
    String nativeOptions = MessageFormat.format(followCommand, tailSize);
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    String fullPrefix = logPath.getType().getPrefix() + CUSTOM_SCHEMA_SEPARATOR;
    String executable = adapterParams.getExecutable();
    log.debug("Starting Docker tracking with executable '{}' and options '{}'...", executable, nativeOptions);
    return new ProcessTailAdapterSpec()
        .executable(executable)
        .file(new ContainerTargetFile(fullPrefix, logPath.getTarget()))
        .id(adapterId)
        .nativeOptions(nativeOptions)
        .fileDelay(trackingProperties.getRetryDelay().toMillis())
        .enableStatusReader(true);
  }

  // TODO consider moving this and fellow methods to corresponding adapters as they are highly specific for the origins
  private MessageProducerSpec<?, ?> newTailAdapter4Kubernetes(LogPath logPath, boolean isTrackingFlat, boolean isTailNeeded) {
    KubernetesOriginAdapter kubernetesOriginAdapter = appContext.getBean(KubernetesOriginAdapter.class);// (!) this will init the adapter firstly!
    GeneralAdapterParams adapterParams = kubernetesOriginAdapter.adapterParams();
    String followCommand = adapterParams.getFollowCommand();
    int tailSize = isTailNeeded
            ? isTrackingFlat
              ? trackingProperties.getTailSize().getFlat()
              : trackingProperties.getTailSize().getGroup()
            : 1;                                 // why 1 see in https://github.com/kubernetes/kubernetes/issues/35335
    String nativeOptions = MessageFormat.format(followCommand, tailSize);
    String adapterId = TAIL_PROCESS_ADAPTER_PREFIX + logPath.getFullPath();
    StringBuilder optsBuilder = new StringBuilder(nativeOptions);
    String resource = null;
    String[] tokens = logPath.getTarget().split("/");
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      switch (token.toLowerCase()) {
        case "namespace":
          String namespace = tokens[i + 1];
          optsBuilder.append(" --namespace=").append(namespace);
          // (!) Caution: namespace might be already specified in adapters.kubernetes.followCommand (application.yaml)
          i++;
          break;
        case "container":
        case "c":
          String container = tokens[i + 1];
          optsBuilder.append(" --container=").append(container);
          i++;
          break;
        default:
          String newResource;
          if ((i + 1) <= (tokens.length - 1)) {   // i.e. if index (i+1) exists in tokens array
            newResource = format("%s/%s", tokens[i], tokens[i + 1]);
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
        .executable(adapterParams.getExecutable())
        .file(new ContainerTargetFile(fullPrefix, resource))
        .id(adapterId)
        .nativeOptions(k8sLogsOptions)
        .fileDelay(trackingProperties.getRetryDelay().toMillis())
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
