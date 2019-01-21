package tech.toparvion.analog.model.api;

import tech.toparvion.analog.model.config.entry.LogType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * @author Toparvion
 */
@SuppressWarnings("unused")   // getters are used by Jackson during serializing the object to JSON
public class LogChoice {

  private final String group;
  /**
   * ID for a plain log is its full path, ID for a composite log is either its uriName or computed hashCode
   */
  private final String id;
  /**
   * String representation of {@linkplain LogType log type}. Must be provided as
   * {@link LogType#toString()}.
   */
  private final String type;
  private final String title;
  private final boolean selected;
  /** Sensible for 'node://' type logs only */
  @Nullable
  private final String node;
  /** Defined for 'composite://' type logs only */
  private final List<String> includes;

  LogChoice(String group,
            String id,
            String type,
            String title,
            boolean selected,
            @Nullable String node,
            List<String> includes) {
    this.group = group;
    this.id = id;
    this.type = type;
    this.node = node;
    this.title = title;
    this.selected = selected;
    this.includes = includes;
  }

  public String getGroup() {
    return group;
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public boolean isSelected() {
    return selected;
  }

  @Nullable
  public String getNode() {
    return node;
  }

  public List<String> getIncludes() {
    return includes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogChoice logChoice = (LogChoice) o;
    return id.equals(logChoice.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
