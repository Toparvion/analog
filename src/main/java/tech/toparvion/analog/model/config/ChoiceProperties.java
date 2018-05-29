package tech.toparvion.analog.model.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.util.AnaLogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings({"unused", "WeakerAccess"})     // such access level and setters presence are required by Spring Boot
@Component
@ConfigurationProperties
public class ChoiceProperties implements InitializingBean {
  private List<ChoiceGroup> choices = new ArrayList<>();

  public List<ChoiceGroup> getChoices() {
    return choices;
  }

  public void setChoices(List<ChoiceGroup> choices) {
    this.choices = choices;
  }

  @Override
  public void afterPropertiesSet() {
    choices.forEach(AnaLogUtils::applyPathBase);
  }
}
