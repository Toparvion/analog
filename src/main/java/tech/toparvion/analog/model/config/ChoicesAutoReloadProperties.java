package tech.toparvion.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Polyudov
 * @since 0.14
 */
@Component
@ConfigurationProperties(prefix = "choices.custom")
public class ChoicesAutoReloadProperties {
  private String location;

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }
}