package ru.ftc.upc.testing.analog.model;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class ChoiceGroup {
  private String group = "(non-grouped)";
  private String pathBase = "";
  private List<String> paths = new ArrayList<>();
  private String scanDir;

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

  public List<String> getPaths() {
    return paths;
  }

  public void setPaths(List<String> paths) {
    this.paths = paths;
  }

  public String getScanDir() {
    return scanDir;
  }

  public void setScanDir(String scanDir) {
    this.scanDir = scanDir;
  }
}
