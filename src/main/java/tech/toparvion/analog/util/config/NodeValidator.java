package tech.toparvion.analog.util.config;

import tech.toparvion.analog.model.config.nodes.Node;

import java.util.List;

import static tech.toparvion.analog.model.config.nodes.Node.NOT_SET;

/**
 * @author Toparvion
 * @since v0.11
 */
public final class NodeValidator {

  public static void validateThisNode(Node thisNode) {
    if (thisNode.getAgentPort() == NOT_SET) {
      throw new IllegalStateException("'nodes.this.agentPort' property must be specified");
    }
  }

  public static void fixOtherNodesPorts(List<Node> others, Node thisNode) {
    for (Node otherNode : others) {
      if (otherNode.getServerPort() == NOT_SET) {
        otherNode.setServerPort(thisNode.getServerPort());
      }
      if (otherNode.getAgentPort() == NOT_SET) {
        otherNode.setAgentPort(thisNode.getAgentPort());
      }
    }
  }
}
