package tech.toparvion.analog.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.toparvion.analog.model.LogChoice;
import tech.toparvion.analog.service.LogChoicesProvider;

import java.util.List;

@RestController
public class ChoiceController {

  private final LogChoicesProvider logChoicesProvider;

  @Autowired
  public ChoiceController(LogChoicesProvider logChoicesProvider) {
    this.logChoicesProvider = logChoicesProvider;
  }

  @GetMapping("/choices")
  public List<LogChoice> choices() {
/*
    Logger logger = LoggerFactory.getLogger(getClass());
    try {
      logger.info("I've come for Dnepr...");
      RestTemplate restTemplate = new RestTemplate();
      restTemplate.getForObject("http://analog.dnepr.ftc.ru:8085", String.class);
    } catch (RestClientException e) {
      logger.error("", e);
    }
*/
    return logChoicesProvider.provideLogChoices();
  }

}