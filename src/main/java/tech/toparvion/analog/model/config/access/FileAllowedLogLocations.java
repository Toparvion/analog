package tech.toparvion.analog.model.config.access;

import java.util.List;

/**
 * Configuration subsection responsible for restricting access to file logs
 * 
 * @author Toparvion
 * @since v0.12
 */
public class FileAllowedLogLocations {
  private List<String> include = List.of();
  private List<String> exclude = List.of();
  private int symlinkResolutionLimit = 1;     // only one link resolution step is permitted by default

  public List<String> getInclude() {
    return include;
  }

  public void setInclude(List<String> include) {
    this.include = include;
  }

  public List<String> getExclude() {
    return exclude;
  }

  public void setExclude(List<String> exclude) {
    this.exclude = exclude;
  }

  public int getSymlinkResolutionLimit() {
    return symlinkResolutionLimit;
  }

  public void setSymlinkResolutionLimit(int symlinkResolutionLimit) {
    this.symlinkResolutionLimit = symlinkResolutionLimit;
  }

  @Override
  public String toString() {
    return "FileAllowedLogLocations{" +
            "include=" + include +
            ", exclude=" + exclude +
            ", symlinkResolutionLimit=" + symlinkResolutionLimit +
            '}';
  }
}
