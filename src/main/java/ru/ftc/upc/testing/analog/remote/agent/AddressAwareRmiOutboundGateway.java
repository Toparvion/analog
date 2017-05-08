package ru.ftc.upc.testing.analog.remote.agent;

import org.springframework.integration.rmi.RmiOutboundGateway;

import java.net.InetSocketAddress;

/**
 * @author Toparvion
 * @since v0.7
 */
class AddressAwareRmiOutboundGateway extends RmiOutboundGateway {

  private final InetSocketAddress gatewayAddress;

  AddressAwareRmiOutboundGateway(InetSocketAddress address, String url) {
    super(url);
    this.gatewayAddress = address;
  }

  InetSocketAddress getGatewayAddress() {
    return gatewayAddress;
  }

  @Override
  public String toString() {
    return "AddressAwareRmiOutboundGateway[" + gatewayAddress + ']';
  }
}
