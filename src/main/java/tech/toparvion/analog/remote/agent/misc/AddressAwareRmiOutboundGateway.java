package tech.toparvion.analog.remote.agent.misc;

import org.springframework.integration.rmi.RmiOutboundGateway;

import java.net.InetSocketAddress;

/**
 * @author Toparvion
 * @since v0.7
 */
public class AddressAwareRmiOutboundGateway extends RmiOutboundGateway {

  private final InetSocketAddress gatewayAddress;

  public AddressAwareRmiOutboundGateway(InetSocketAddress address, String url) {
    super(url);
    this.gatewayAddress = address;
  }

  public InetSocketAddress getGatewayAddress() {
    return gatewayAddress;
  }

  @Override
  public String toString() {
    return "AddressAwareRmiOutboundGateway[" + gatewayAddress + ']';
  }
}
