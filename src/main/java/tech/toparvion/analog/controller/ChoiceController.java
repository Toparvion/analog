package tech.toparvion.analog.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.toparvion.analog.model.api.LogChoice;
import tech.toparvion.analog.service.choice.LogChoicesProvider;

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
    return logChoicesProvider.provideLogChoices();
  }

}