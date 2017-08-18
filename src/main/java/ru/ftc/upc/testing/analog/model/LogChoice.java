package ru.ftc.upc.testing.analog.model;

import javax.annotation.Nullable;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")   // getters are used by Jackson during serializing the object to JSON
public class LogChoice {
  private final String group;
  private final String path;
  private final String encoding;
  private final String title;
  private final boolean selectedByDefault;
  @Nullable   // null in case of plain (not composite) log
  private final String uid;
  @Nullable   // null in case of not explicitly specified node
  private final String node;

  public LogChoice(String group, String path, String encoding, String title, boolean selectedByDefault,
                 String uid) {
    this(group, path, encoding, title, selectedByDefault, uid, null);
  }

  public LogChoice(String group, String path, String encoding, String title, boolean selectedByDefault,
                   @Nullable String uid, @Nullable String node) {
    this.group = group;
    this.node = node;
    String forwardSlashedPath = path.replaceAll("\\\\", "/");
    this.path = forwardSlashedPath.startsWith("/")
            ? forwardSlashedPath
            : ("/" + forwardSlashedPath);
    this.encoding = encoding;
    this.title = title;
    this.selectedByDefault = selectedByDefault;
    this.uid = uid;
  }

  public String getGroup() {
    return group;
  }

  public String getPath() {
    return path;
  }

  public String getEncoding() {
    return encoding;
  }

  public String getTitle() {
    return title;
  }

  public boolean getSelectedByDefault() {
    return selectedByDefault;
  }

  @Nullable
  public String getUid() {
    return uid;
  }

  @Nullable
  public String getNode() {
    return node;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LogChoice logChoice = (LogChoice) o;

    return path.equals(logChoice.path);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }
}
