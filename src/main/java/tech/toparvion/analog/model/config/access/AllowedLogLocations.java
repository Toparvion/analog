package tech.toparvion.analog.model.config.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Root config section for storing settings of all kinds of log access restrictions e.g. access to files, to Docker, etc.
 * @author Toparvion
 * @since v0.12
 */
@Component
@ConfigurationProperties(prefix = "allowed-log-locations")
public class AllowedLogLocations {
  private FileAllowedLogLocations file;
  
  // Future implementations:
  // private DockerAllowedLogLocations docker;
  // private KubernetesAllowedLogLocations kubernetes;

  public FileAllowedLogLocations getFile() {
    return file;
  }

  public void setFile(FileAllowedLogLocations file) {
    this.file = file;
  }

  @Override
  public String toString() {
    return "AllowedLogLocations{" +
            "file=" + file +
            '}';
  }
}
