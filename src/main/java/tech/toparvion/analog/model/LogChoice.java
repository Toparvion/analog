package tech.toparvion.analog.model;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")   // getters are used by Jackson during serializing the object to JSON
public class LogChoice {
  private final String group;
  private final String path;
  private final String title;
  private final boolean selectedByDefault;
  @Nullable   // null in case of plain (not composite) log
  private final String uid;
  private final List<String> nodes = new ArrayList<>();

  public LogChoice(String group, String path, String title, boolean selectedByDefault,
                   @Nullable String uid, List<String> nodes) {
    this.group = group;
    this.nodes.addAll(nodes);
    String forwardSlashedPath = path.replaceAll("\\\\", "/");
    this.path = forwardSlashedPath.startsWith("/")
            ? forwardSlashedPath
            : ("/" + forwardSlashedPath);
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

  public List<String> getNodes() {
    return nodes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogChoice logChoice = (LogChoice) o;
    return Objects.equals(path, logChoice.path) &&
            Objects.equals(uid, logChoice.uid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, uid);
  }
}
