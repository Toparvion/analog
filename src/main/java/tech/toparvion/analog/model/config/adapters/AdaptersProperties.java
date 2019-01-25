package tech.toparvion.analog.model.config.adapters;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Toparvion
 * @since 0.11
 */
@Component
@ConfigurationProperties("adapters")
public class AdaptersProperties {

  private TrackingProperties all;
  private FileAdapterParamSection file;
  private ContainerAdapterParams docker;
  private ContainerAdapterParams kubernetes;

  public TrackingProperties getAll() {
    return all;
  }

  public void setAll(TrackingProperties all) {
    this.all = all;
  }

  public FileAdapterParamSection getFile() {
    return file;
  }

  public void setFile(FileAdapterParamSection file) {
    this.file = file;
  }

  public ContainerAdapterParams getDocker() {
    return docker;
  }

  public void setDocker(ContainerAdapterParams docker) {
    this.docker = docker;
  }

  public ContainerAdapterParams getKubernetes() {
    return kubernetes;
  }

  public void setKubernetes(ContainerAdapterParams kubernetes) {
    this.kubernetes = kubernetes;
  }

  @Override
  public String toString() {
    return "AdaptersProperties{" +
            "all=" + all +
            ", file=" + file +
            ", docker=" + docker +
            ", kubernetes=" + kubernetes +
            '}';
  }
}
