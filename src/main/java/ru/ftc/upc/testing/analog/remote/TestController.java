package ru.ftc.upc.testing.analog.remote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Toparvion on 15.01.2017.
 */
@RestController
public class TestController {

  private final RemoteGateway remoteGateway;

  @Autowired
  public TestController(RemoteGateway remoteGateway) {
    this.remoteGateway = remoteGateway;
  }

  @RequestMapping("/start-watching")
  public void startWatching(@RequestParam String logPath,
                            @RequestParam String timestampFormat) {

    remoteGateway.switchRegistration(logPath,
                                     timestampFormat,
                                     true);
  }

  @RequestMapping("/stop-watching")
  public void stopWatching(@RequestParam String logPath) {
    remoteGateway.switchRegistration(logPath,
                                     null,
                                     false);
  }
}
