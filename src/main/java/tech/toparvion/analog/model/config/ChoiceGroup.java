package tech.toparvion.analog.model.config;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class ChoiceGroup {

  private String group                          = "(non-grouped)";
  private String pathBase                       = "";
  private String scanDir                        = null;
  private List<LogConfigEntry> compositeLogs    = new ArrayList<>();
  private List<String> plainLogs                = new ArrayList<>();

  public ChoiceGroup() { }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getPathBase() {
    return pathBase;
  }

  public void setPathBase(String pathBase) {
    this.pathBase = pathBase;
  }

  public String getScanDir() {
    return scanDir;
  }

  public void setScanDir(String scanDir) {
    this.scanDir = scanDir;
  }

  public List<LogConfigEntry> getCompositeLogs() {
    if (StringUtils.hasText(pathBase)) {
      return compositeLogs.stream()
              .map(entry -> entry.prependPath(pathBase))
              .collect(toList());
    } else {
      return compositeLogs;
    }
  }

  public void setCompositeLogs(List<LogConfigEntry> compositeLogs) {
    this.compositeLogs = compositeLogs;
  }

  public List<String> getPlainLogs() {
    return plainLogs;
  }

  public void setPlainLogs(List<String> plainLogs) {
    this.plainLogs = plainLogs;
  }
}
