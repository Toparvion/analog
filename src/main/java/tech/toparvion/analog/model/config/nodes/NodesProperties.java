package tech.toparvion.analog.model.config.nodes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tech.toparvion.analog.util.config.NodeValidator;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings({"unused"})     // setters presence are required by Spring Boot
@Component
@ConfigurationProperties(prefix = "nodes")
public class NodesProperties {

  private Node thisNode;
  private List<Node> others = new ArrayList<>();
  /**
   * Properties for registration of nodes in service registry, e.g. Eureka. The presence of this field would indicate
   * that explicit node's coordinates are required (i.e. no host/serverPort/agentPort).
   */
  // private ServiceRegistryProperties serviceRegistryProperties;
  
  public Node getThis() {
    return thisNode;
  }

  public void setThis(Node thisNode) {
    this.thisNode = thisNode;
  }

  public List<Node> getOthers() {
    return others;
  }

  public void setOthers(List<Node> others) {
    this.others = others;
  }

  public Node findNodeByName(String name) {
    Assert.hasText(name, "node name must be specified");
    if (name.equalsIgnoreCase(thisNode.getName())) {
      return thisNode;
    }
    return others.stream()
        .filter(node -> name.equals(node.getName()))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(format(
            "No node with name '%s' found among 'nodes' in configuration.", name)));
  }

  @PostConstruct
  public void validateAndFix() {
    NodeValidator.validateThisNode(thisNode);
    NodeValidator.fixOtherNodesPorts(others, thisNode);
  }

}
