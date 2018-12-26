package tech.toparvion.analog.model.config.entry;

import java.util.Objects;

/**
 * @author Toparvion
 * @since v0.11
 */
public class CompositeInclusion {
  private LogPath path;
  private String timestamp;

  public LogPath getPath() {
    return path;
  }

  public void setPath(LogPath path) {
    this.path = path;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CompositeInclusion that = (CompositeInclusion) o;
    return path.equals(that.path) &&
        timestamp.equals(that.timestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, timestamp);
  }

  @Override
  public String toString() {
    return "CompositeInclusion{" +
        "path='" + path.toString() + '\'' +
        ", timestamp='" + timestamp + '\'' +
        '}';
  }
}
