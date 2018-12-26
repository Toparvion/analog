package tech.toparvion.analog.model.config.nodes;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class Node {
  public static final int NOT_SET = -1;

  private String name;
  private String host;
  private int agentPort;
  private int serverPort = NOT_SET;

  public Node() { }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setAgentPort(int agentPort) {
    this.agentPort = agentPort;
  }

  public void setServerPort(int serverPort) {
    this.serverPort = serverPort;
  }

  public String getHost() {
    return host;
  }

  public int getAgentPort() {
    return agentPort;
  }

  public int getServerPort() {
    return serverPort;
  }

  public InetSocketAddress getAgentInetSocketAddress() {
    return new InetSocketAddress(host, agentPort);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Node that = (Node) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
