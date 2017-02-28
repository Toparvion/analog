package ru.ftc.upc.testing.analog.remote.server;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Header;

import static ru.ftc.upc.testing.analog.remote.CommonTrackingConstants.*;

/**
 * A gateway to the logs tracking control.<p>
 * Created by Toparvion on 13.01.2017.
 */
@MessagingGateway(defaultRequestChannel = SERVER_REGISTRATION_RMI_OUT__CHANNEL)
public interface RemoteGateway {

  /**
   * Entry point for switching log tracking on and off. A part of server side.
   * @param logPath absolute path to tracking log file
   * @param timestampFormat format of log timestamp in the form of {@link java.time.format.DateTimeFormatter};
   *                        required for registration mode only (when {@code doRegister==true})
   * @param doRegister a flag indicating the mode of the operation: {@code true} for registering new tracking and
   * {@code false} for unregistering it
   */
  void switchRegistration(String logPath,
                          @Header(value = LOG_TIMESTAMP_FORMAT__HEADER, required = false) String timestampFormat,
                          @Header(value = REGISTRATION_MODE__HEADER) boolean doRegister);

}
