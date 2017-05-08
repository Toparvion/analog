package ru.ftc.upc.testing.analog.remote.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple REST controller for debugging cluster routines without frontend (through IDEA REST Client)
 *
 * @author Toparvion
 * @since v0.7
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
                                     "angara",
                                     timestampFormat,
                                     true);
  }

  @RequestMapping("/stop-watching")
  public void stopWatching(@RequestParam String logPath) {
    remoteGateway.switchRegistration(logPath,
                                     "angara",
                                     null,
                                     false);
  }
}
