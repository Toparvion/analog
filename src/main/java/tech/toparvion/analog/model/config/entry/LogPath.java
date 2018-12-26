package tech.toparvion.analog.model.config.entry;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Toparvion
 * @since v0.11
 */
public class LogPath implements Serializable {
  private LogType type;
  private String node;
  /**
   * A path to the target log, e.g. {@code /home/me/app.log} for a file or {@code deploy/my-pod} for a K8s pod.
   * In case of local file the target is the same as {@link #fullPath}, but in all other cases it's just the
   * last part of the {@link #fullPath}
   */
  private String target;
  /**
   * The path as it was when constructing this object
   */
  private String fullPath;

  public LogPath() { }

  public String getTarget() {
    return target;
  }

  public LogType getType() {
    return type;
  }

  public String getNode() {
    return node;
  }

  public String getFullPath() {
    return fullPath;
  }

  public void setType(LogType type) {
    this.type = type;
  }

  public void setNode(String node) {
    this.node = node;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public void setFullPath(String fullPath) {
    this.fullPath = fullPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogPath that = (LogPath) o;
    return type == that.type &&
        node.equals(that.node) &&
        target.equals(that.target) &&
        fullPath.equals(that.fullPath);
  }

  @Override
  public int hashCode() {
    // here we use LogType#toString() to guarantee stable hashCode value of log type over JVM launch sessions
    return Objects.hash(type.toString(), node, target, fullPath);
  }

  @Override
  public String toString() {
    return "LogPath{" +
        "type=" + type.toString() +
        ", node='" + node + '\'' +
        ", target='" + target + '\'' +
        ", fullPath='" + fullPath + '\'' +
        '}';
  }
}
