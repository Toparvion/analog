package tech.toparvion.analog.util.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.Locale;
import java.util.Map;

/**
 * Environment post processor capable of adding some 'automatically computed' properties
 * @author Toparvion
 * @since 0.11
 */
public class ConfigurationBrusher implements EnvironmentPostProcessor {
  private static final String PROPERTY_SOURCE_NAME = "defaultProperties";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    String serverHost = environment.getProperty("server.address", "localhost");
    String serverPort = environment.getProperty("server.port", "8083");
    Map<String, Object> autoAddedProps = Map.of(
            "nodes.this.host",        serverHost,
            "nodes.this.serverPort",  serverPort);
    addOrReplace(environment.getPropertySources(), autoAddedProps);
    // There no use to log here as logging subsystem is not initialized yet
    // log.info("Added default properties for 'this' node: {}", autoAddedProps);

    String locale = environment.getProperty("spring.mvc.locale", "en");
    LocaleContextHolder.setDefaultLocale(new Locale(locale));
  }

  private void addOrReplace(MutablePropertySources propertySources,
                            Map<String, Object> map) {
    MapPropertySource target = null;
    if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
      PropertySource<?> source = propertySources.get(PROPERTY_SOURCE_NAME);
      if (source instanceof MapPropertySource) {
        target = (MapPropertySource) source;
        for (String key : map.keySet()) {
          if (!target.containsProperty(key)) {
            target.getSource().put(key, map.get(key));
          }
        }
      }
    }
    if (target == null) {
      target = new MapPropertySource(PROPERTY_SOURCE_NAME, map);
    }
    if (!propertySources.contains(PROPERTY_SOURCE_NAME)) {
      propertySources.addLast(target);
    }
  }
}
