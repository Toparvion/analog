package tech.toparvion.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.util.config.ChoiceValidator;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings({"unused"})     // setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class ChoiceProperties {
  private List<ChoiceGroup> choices = new ArrayList<>();

  public List<ChoiceGroup> getChoices() {
    return choices;
  }

  public void setChoices(List<ChoiceGroup> choices) {
    this.choices = choices;
  }

  @PostConstruct
  public void tuneProperties() {
    ChoiceValidator.applyPathBase(choices);
    ChoiceValidator.checkAndFixSelectedEntry(choices);
  }

  @Override
  public String toString() {
    return "ChoiceProperties{" +
        "groupsCount=" + choices.size() +
        '}';
  }
}
