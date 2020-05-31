package tech.toparvion.analog.util.config;

import org.apache.commons.io.FilenameUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Environment post processor capable of loading choices list property from custom file.<br/>
 *
 * @author Polyudov
 * @implNote <ul>
 * <li>There are no logs because the logging subsystem is not initialized yet</li>
 * <li>Works only with <code>.yaml/.yml</code> files</li>
 * </ul>
 * @since v0.14
 */
public class ChoicesCustomConfigurationLoader implements EnvironmentPostProcessor {
  private static final String CHOICES_RESOURCE_NAME = "choicesCustomResource";
  private final PropertySourceLoader loader;

  @SuppressWarnings("unused") //for Spring
  public ChoicesCustomConfigurationLoader() {
    this.loader = new YamlPropertySourceLoader();
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    String choicesPath = environment.getProperty("choices.custom.location");
    if (isNullOrEmpty(choicesPath)) {
      return;
    }

    FileSystemResource resource = new FileSystemResource(choicesPath);
    if (!resource.isFile() || !resource.isReadable() || !isExtensionCorrect(resource.getFilename())) {
      return;
    }

    //Create new PropertySource or replace existing PropertySource
    //if PropertySource with name 'choicesResource' already exists
    loadPropertySource(resource)
        .ifPresent(propertySource -> environment.getPropertySources().addFirst(propertySource));
  }

  private boolean isExtensionCorrect(String fileName) {
    String extension = FilenameUtils.getExtension(fileName);
    if (isNullOrEmpty(extension)) {
      return false;
    }
    return Arrays.asList(loader.getFileExtensions()).contains(extension);
  }

  private Optional<PropertySource<?>> loadPropertySource(Resource resource) {
    try {
      List<PropertySource<?>> propertySources = loader.load(CHOICES_RESOURCE_NAME, resource);
      if (isEmpty(propertySources)) {
        return Optional.empty();
      }
      //There are more than one PropertySource as a result of load()-method in some cases
      //because YAML is a multi-document format. But we use only the first document and ignore another ones
      return Optional.ofNullable(propertySources.get(0));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
