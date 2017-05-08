package ru.ftc.upc.testing.analog.model.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")       // setters are used by Spring while processing @ConfigurationProperties
public class LogConfigEntry {
  private String path;
  private String node;
  private String title;
  private boolean selected = false;
  private String encoding;
  private String timestamp = "dd.MM.yy HH:mm:ss,SSS";
  private List<LogConfigEntry> includes = new ArrayList<>();

  public LogConfigEntry() { }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getNode() {
    return node;
  }

  public void setNode(String node) {
    this.node = node;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public List<LogConfigEntry> getIncludes() {
    return includes;
  }

  public void setIncludes(List<LogConfigEntry> includes) {
    this.includes = includes;
  }

  public String getUid() {
    return Integer.toHexString(hashCode());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogConfigEntry that = (LogConfigEntry) o;
    return selected == that.selected &&
        Objects.equals(path, that.path) &&
        Objects.equals(node, that.node) &&
        Objects.equals(title, that.title) &&
        Objects.equals(encoding, that.encoding) &&
        Objects.equals(timestamp, that.timestamp) &&
        Objects.equals(includes, that.includes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, node, title, selected, encoding, timestamp, includes);
  }

  @Override
  public String toString() {
    return "LogConfigEntry{" +
        "path='" + path + '\'' +
        ", node='" + node + '\'' +
        ", title='" + title + '\'' +
        ", selected=" + selected +
        ", encoding='" + encoding + '\'' +
        ", timestamp='" + timestamp + '\'' +
        ", includesSize=" + includes.size() +
        '}';
  }
}
