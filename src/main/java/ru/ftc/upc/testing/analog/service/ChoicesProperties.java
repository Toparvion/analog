package ru.ftc.upc.testing.analog.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.ChoiceGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings({"unused", "WeakerAccess"})     // such access level and setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class ChoicesProperties {
  private List<ChoiceGroup> choices = new ArrayList<>();

  public List<ChoiceGroup> getChoices() {
    return choices;
  }

  public void setChoices(List<ChoiceGroup> choices) {
    this.choices = choices;
  }
}
