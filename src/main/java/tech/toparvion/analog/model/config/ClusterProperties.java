package tech.toparvion.analog.model.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings({"unused", "WeakerAccess"})     // such access level and setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class ClusterProperties {
  private List<ClusterNode> nodes = new ArrayList<>();
  private final int serverPort;

  public ClusterProperties(@Value("${server.port}") int serverPort) {
    assert serverPort != 0;
    this.serverPort = serverPort;
  }

  public List<ClusterNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<ClusterNode> nodes) {
    this.nodes = nodes;
  }

  public ClusterNode getMyselfNode() {
    assert !nodes.isEmpty();

    return nodes.stream()
        .filter(ClusterNode::getMyself)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No node found that corresponds to current machine. " +
            "Please specify it explicitly by adding 'myself: true' attribute to corresponding node " +
            "definition in application configuration ('nodes' entry)."));
  }

  public ClusterNode findNodeByName(String name) {
    return nodes.stream()
        .filter(node -> name.equals(node.getName()))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(format(
            "No node with name '%s' found among 'nodes' in configuration.", name)));
  }

  public int resolveServerPortFor(ClusterNode node) {
    return (node.getServerPort() != 0)
        ? node.getServerPort()
        : serverPort;
  }
}
