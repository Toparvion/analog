package ru.ftc.upc.testing.analog.remote;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Header;

/**
 * Created by Toparvion on 13.01.2017.
 */
@MessagingGateway(defaultRequestChannel = RemoteConfig.REGISTER_RMI_OUT_CHANNEL_ID)
public interface RemoteGateway {

  void switchRegistration(String logPath, @Header(RemoteConfig.REGISTRATION_MODE_HEADER_NAME) boolean doRegister);

}
