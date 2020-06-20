package tech.toparvion.analog.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.toparvion.analog.model.api.LogChoice;
import tech.toparvion.analog.service.choice.LogChoicesComposer;

import java.util.List;

@RestController
public class ChoiceController {

  private final LogChoicesComposer logChoicesComposer;

  @Autowired
  public ChoiceController(LogChoicesComposer logChoicesComposer) {
    this.logChoicesComposer = logChoicesComposer;
  }

  @GetMapping("/choices")
  public List<LogChoice> choices() {
    return logChoicesComposer.provideLogChoices();
  }

}