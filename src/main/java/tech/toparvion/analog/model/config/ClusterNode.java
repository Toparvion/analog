package tech.toparvion.analog.model.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private String address;
  private Boolean myself;

  // synthetic (computed) fields
  private String host;
  private int port;

  public ClusterNode() { }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
    // additionally parse the address string to extract host and port values
    Objects.requireNonNull(address);
    String[] tokens = address.split(":");
    assert tokens.length > 0;
    host = tokens[0];
    port = (tokens.length > 1)
        ? Integer.valueOf(tokens[1])
        : 80;
  }

  boolean getMyself() {
    if (myself != null) {
      return myself;
    }
    defineMyself();
    return myself;
  }

  public void setMyself(Boolean myself) {
    this.myself = myself;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public InetSocketAddress getInetSocketAddress() {
    return new InetSocketAddress(host, port);
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
}
