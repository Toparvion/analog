package tech.toparvion.analog.model.config.entry;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 * @since v0.11
 */
@SuppressWarnings("unused")     // setters are used by Spring while reading the configuration
public class ScanLocations {
  private List<String> directories  = new ArrayList<>();
  private List<String> docker       = new ArrayList<>();
  private List<String> kubernetes   = new ArrayList<>();

  public ScanLocations() { }

  public List<String> getDirectories() {
    return directories;
  }

  public void setDirectories(List<String> directories) {
    this.directories = directories;
  }

  public List<String> getDocker() {
    return docker;
  }

  public void setDocker(List<String> docker) {
    this.docker = docker;
  }

  public List<String> getKubernetes() {
    return kubernetes;
  }

  public void setKubernetes(List<String> kubernetes) {
    this.kubernetes = kubernetes;
  }

  @Override
  public String toString() {
    return "ScanLocations{" +
        "directoriesSize=" + directories.size() +
        ", dockerSize=" + docker.size() +
        ", kubernetesSize=" + kubernetes.size() +
        '}';
  }
}
