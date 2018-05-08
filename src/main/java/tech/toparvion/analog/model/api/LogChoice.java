package tech.toparvion.analog.model.api;

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
  private final String node;
  private final boolean remote;
  private final String title;
  private final boolean selected;
  @Nullable   // null in case of plain (not composite) log
  private final String uid;
  private final List<CompositeInclusion> includes = new ArrayList<>();

  LogChoice(String group,
            String path,
            String node,
            boolean remote,
            String title,
            boolean selected,
            @Nullable String uid,
            List<CompositeInclusion> includes) {
    this.group = group;
    this.path = path;
    this.node = node;
    this.remote = remote;
    this.title = title;
    this.selected = selected;
    this.uid = uid;
    this.includes.addAll(includes);
  }

  public String getGroup() {
    return group;
  }

  public String getPath() {
    return path;
  }

  public boolean getRemote() {
    return remote;
  }

  public String getTitle() {
    return title;
  }

  public String getNode() {
    return node;
  }

  public boolean getSelected() {
    return selected;
  }

  @Nullable
  public String getUid() {
    return uid;
  }

  public List<CompositeInclusion> getIncludes() {
    return includes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogChoice logChoice = (LogChoice) o;
    return Objects.equals(path, logChoice.path) &&
        Objects.equals(node, logChoice.node) &&
        Objects.equals(uid, logChoice.uid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, node, uid);
  }
}
