package tech.toparvion.analog.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.toparvion.analog.model.config.nodes.Node;

import java.net.InetAddress;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.model.config.nodes.Node.NOT_SET;

/**
 * @author Toparvion
 * @since v0.11
 */
public final class NodeValidator {
  private static final Logger log = LoggerFactory.getLogger(NodeValidator.class);

  public static void validateThisNode(Node thisNode, int serverPort) {
    if (thisNode.getServerPort() != NOT_SET) {
      log.warn("'serverPort: {}' should not be specified for 'this' node as the port is already defined " +
          "in 'server.port: {}' property. The value of 'nodes.this.serverPort' will be ignored.",
          thisNode.getServerPort(), serverPort);
    }
    if (hasText(thisNode.getHost())) {
      log.warn("'host: {}' should not be specified for 'this' node as it is automatically determined from loopback " +
          "network interface", thisNode.getHost());
    }
    thisNode.setHost(InetAddress.getLoopbackAddress().getHostName());
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
