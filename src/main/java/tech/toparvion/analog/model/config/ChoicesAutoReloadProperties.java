package tech.toparvion.analog.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.service.choice.ConditionalOnChoicesAutoReloadEnabled;
import tech.toparvion.analog.util.config.ChoicesCustomConfigurationLoader;

/**
 * @author Polyudov
 * @since v0.14
 */
@Component
@SuppressWarnings("unused") // setters are required by Spring Boot
@ConfigurationProperties("choices-source")
public class ChoicesAutoReloadProperties {
  /**
   * Path to custom {@linkplain ChoiceProperties choices} location
   *
   * @see ChoicesCustomConfigurationLoader
   */
  private String location;

  /**
   * This field {@linkplain ConditionalOnChoicesAutoReloadEnabled used} for enable/disable auto reloading
   */
  private Boolean autoReloadEnabled;

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public Boolean isAutoReloadEnabled() {
    return autoReloadEnabled;
  }

  public void setAutoReloadEnabled(Boolean autoReloadEnabled) {
    this.autoReloadEnabled = autoReloadEnabled;
  }
}
