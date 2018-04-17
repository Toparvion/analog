package tech.toparvion.analog.model.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import static java.lang.String.format;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class ClusterNode {
  private static final Logger log = LoggerFactory.getLogger(ClusterNode.class);

  private String name;
  private String host;
  private int agentPort;
  private int serverPort;
  @Nullable
  private Boolean myself;

  public ClusterNode() { }

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

  public void setMyself(@Nullable Boolean myself) {
    this.myself = myself;
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

  boolean getMyself() {
    if (myself != null) {
      return myself;
    }
    defineMyself();
    return myself;
  }

  public InetSocketAddress getAgentInetSocketAddress() {
    return new InetSocketAddress(host, agentPort);
  }

  private void defineMyself() {
    Objects.requireNonNull(host);
    try {
      InetAddress hostAddress = InetAddress.getByName(host);
      InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
      // TODO search for myself node not by comparison with loopback address only but with all the network interfaces
      myself = loopbackAddress.equals(hostAddress);
      log.trace("Host address: {}; loopbackAddress: {}; myself: {}", hostAddress, loopbackAddress, myself);

    } catch (UnknownHostException e) {
      log.error(format("Unable to resolve address for host '%s'. 'Myself' is being set to false.", host), e);
      myself = false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClusterNode that = (ClusterNode) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
