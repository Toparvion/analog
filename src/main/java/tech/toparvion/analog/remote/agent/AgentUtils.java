package tech.toparvion.analog.remote.agent;

import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import tech.toparvion.analog.model.remote.TrackingRequest;

import java.net.InetSocketAddress;
import java.util.LinkedList;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static tech.toparvion.analog.remote.agent.AgentConstants.FLAT_PREFIX;
import static tech.toparvion.analog.remote.agent.AgentConstants.GROUP_PREFIX;

/**
 * @author Toparvion
 * @since v0.12
 */
final class AgentUtils {
  private AgentUtils() {}

  /**
   * Finds and returns the latest pub-sub channel of given flow. <br>
   * To improve: if this method become source of errors, we can avoid its usage by means of referencing the output
   * channel name by its name only. Of course, such a name must be well-defined by corresponding tracking flow. See
   * examples in {@code TailingFlowProvider#findOrCreateTailFlow()}.
   *
   * @param logTrackingFlow tracking flow to extract output channel from
   * @return the latest pub-sub channel of the flow
   * @throws IllegalStateException if channel was not found
   */
  static PublishSubscribeChannel extractOutChannel(StandardIntegrationFlow logTrackingFlow) throws IllegalStateException {
    LinkedList<PublishSubscribeChannel> channels = logTrackingFlow.getIntegrationComponents()
        .keySet()
        .stream()
        .filter(PublishSubscribeChannel.class::isInstance)
        .map(PublishSubscribeChannel.class::cast)
        .collect(toCollection(LinkedList::new));
    if (channels.isEmpty()) {
      throw new IllegalStateException(format("No PublishSubscribeChannel found among components of logTrackingFlow: %s",
          logTrackingFlow.getIntegrationComponents().keySet()
              .stream()
              .map(Object::toString)
              .collect(joining())));
    }
    return channels.getLast();
  }

  static String composeSendingFlowId(TrackingRequest request, InetSocketAddress watcherAddress) {
    String logPath = request.getLogPath().getFullPath();
    String flowsPrefix = request.isFlat() ? FLAT_PREFIX : GROUP_PREFIX;
    return String.format("%s%s_%s", flowsPrefix, watcherAddress, logPath);
  }

  static String composeTrackingFlowId(String flowPrefix, String logPath) {
    return flowPrefix + logPath;
  }
}
