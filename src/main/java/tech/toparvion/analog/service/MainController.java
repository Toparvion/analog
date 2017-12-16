package tech.toparvion.analog.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.toparvion.analog.model.LogChoice;

import java.util.List;

@RestController
public class MainController {

  private final LogChoicesProvider logChoicesProvider;

  @Autowired
  public MainController(LogChoicesProvider logChoicesProvider) {
    this.logChoicesProvider = logChoicesProvider;
  }

  @RequestMapping("/choices")
  public List<LogChoice> choices() {
    return logChoicesProvider.provideLogChoices();
  }

}