package ru.ftc.upc.testing.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings({"unused", "WeakerAccess"})     // such access level and setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class ClusterProperties {
  private List<ClusterNode> clusterNodes = new ArrayList<>();

  public List<ClusterNode> getClusterNodes() {
    return clusterNodes;
  }

  public void setClusterNodes(List<ClusterNode> clusterNodes) {
    this.clusterNodes = clusterNodes;
  }

  public ClusterNode getMyselfNode() {
    assert !clusterNodes.isEmpty();

    return clusterNodes.stream()
        .filter(ClusterNode::getMyself)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No cluster node found that corresponds to current machine. " +
            "Please specify it explicitly by adding 'myself: true' attribute to corresponding cluster node " +
            "definition in application configuration ('clusterNodes' entry)."));
  }
}
