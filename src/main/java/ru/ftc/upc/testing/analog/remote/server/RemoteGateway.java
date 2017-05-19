package ru.ftc.upc.testing.analog.remote.server;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Header;
import ru.ftc.upc.testing.analog.model.TrackingRequest;

import static ru.ftc.upc.testing.analog.remote.RemotingConstants.REGISTRATION_MODE__HEADER;
import static ru.ftc.upc.testing.analog.remote.RemotingConstants.SERVER_REGISTRATION_ROUTER__CHANNEL;

/**
 * A gateway to the logs tracking control.
 *
 * @author Toparvion
 * @since v0.7
 */
@MessagingGateway(defaultRequestChannel = SERVER_REGISTRATION_ROUTER__CHANNEL)
public interface RemoteGateway {

  /**
   * Entry point for switching log tracking on and off. A part of server side.
   * @param trackingRequest object encapsulating all the parameters needed for the agent to process
   * @param doRegister a flag indicating the mode of the operation: {@code true} for registering new tracking and
   * {@code false} for unregistering it
   */
  void switchRegistration(TrackingRequest trackingRequest,
                          @Header(value = REGISTRATION_MODE__HEADER) boolean doRegister);
}
