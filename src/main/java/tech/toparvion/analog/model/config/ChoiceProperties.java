package tech.toparvion.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
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
@RefreshScope
@ConfigurationProperties(prefix = "choices")
public class ChoiceProperties {
  private List<ChoiceGroup> list = new ArrayList<>();

  public List<ChoiceGroup> getList() {
    return list;
  }

  public void setList(List<ChoiceGroup> list) {
    this.list = list;
  }

  @PostConstruct
  public void tuneProperties() {
    ChoiceValidator.applyPathBase(list);
    ChoiceValidator.checkAndFixSelectedEntry(list);
  }

  @Override
  public String toString() {
    return "ChoiceProperties{" +
        "groupsCount=" + list.size() +
        '}';
  }
}
