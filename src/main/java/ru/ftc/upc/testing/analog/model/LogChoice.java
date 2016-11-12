package ru.ftc.upc.testing.analog.model;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")   // getters are used by Jackson during serializing the object to JSON
public class LogChoice {
  private final String group;
  private final String path;
  private final String title;
  private final boolean selectedByDefault;

  public LogChoice(String group, String path, String title, boolean selectedByDefault) {
    this.group = group;
    this.path = path;
    this.title = title;
    this.selectedByDefault = selectedByDefault;
  }

  public String getGroup() {
    return group;
  }

  public String getPath() {
    return path;
  }

  public String getTitle() {
    return title;
  }

  public boolean getSelectedByDefault() {
    return selectedByDefault;
  }
}
