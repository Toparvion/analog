package ru.ftc.upc.testing.analog.remote;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Header;

import static ru.ftc.upc.testing.analog.remote.RemoteConfig.*;

/**
 * Created by Toparvion on 13.01.2017.
 */
@MessagingGateway(defaultRequestChannel = REGISTER_RMI_OUT_CHANNEL_ID)
public interface RemoteGateway {

  /**
   *
   * @param logPath
   * @param timestampFormat required for registration mode only (when {@code doRegister==true})
   * @param doRegister
   */
  void switchRegistration(String logPath,
                          @Header(value = LOG_TIMESTAMP_HEADER, required = false) String timestampFormat,
                          @Header(value = REGISTRATION_MODE_HEADER_NAME) boolean doRegister);

}
