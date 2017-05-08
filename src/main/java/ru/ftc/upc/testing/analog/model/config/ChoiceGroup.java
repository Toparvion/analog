package ru.ftc.upc.testing.analog.model.config;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class ChoiceGroup {

  private String group                = "(non-grouped)";
  private String encoding             = null;
  private String pathBase             = "";
  private String scanDir              = null;
  private List<LogConfigEntry> logs   = new ArrayList<>();

  public ChoiceGroup() { }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
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

  public List<LogConfigEntry> getLogs() {
    return logs;
  }

  public void setLogs(List<LogConfigEntry> logs) {
    this.logs = logs;
  }
}
