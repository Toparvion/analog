package ru.ftc.upc.testing.analog.model;

import java.io.File;

/**
 * @author Toparvion
 */
public class LogChoice {
  private final String group;
  private final String path;
  private final String fileName;
  private final boolean selectedByDefault;

  public LogChoice(String group, String path) {
    this(group, path, false);
  }

  public LogChoice(String group, String path, boolean selectedByDefault) {
    this.group = group;
    this.path = path;
    this.fileName = path.substring(path.lastIndexOf(File.separatorChar)+1);
    this.selectedByDefault = selectedByDefault;
  }

  public String getGroup() {
    return group;
  }

  public String getPath() {
    return path;
  }

  public String getFileName() {
    return fileName;
  }

  public boolean getSelectedByDefault() {
    return selectedByDefault;
  }
}
