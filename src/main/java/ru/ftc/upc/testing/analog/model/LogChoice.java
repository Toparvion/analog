package ru.ftc.upc.testing.analog.model;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")   // getters are user by Jackson during JSON serializing
public class LogChoice {
  private final String group;
  private final String path;
  private final String fileName;
  private final boolean selectedByDefault;

  public LogChoice(String group, String path, String fileName, boolean selectedByDefault) {
    this.group = group;
    this.path = path;
    this.fileName = fileName;
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
