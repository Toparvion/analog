package tech.toparvion.analog.model.api;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.util.AnaLogUtils.convertToUnixStyle;

/**
 * Helper class aimed to simplicfy log choice instances construction
 *
 * @author Toparvion
 * @since v0.10
 */
public class LogChoiceBuilder {
  private String group;
  private String path;
  private String node;
  private boolean remote = false;
  private String title;
  private boolean selected = false;
  private String uid = null;
  private List<CompositeInclusion> includes = List.of();

  public LogChoiceBuilder setGroup(String group) {
    this.group = group;
    return this;
  }

  public LogChoiceBuilder setPath(String path) {
    this.path = path;
    return this;
  }

  public LogChoiceBuilder setNode(String node) {
    this.node = node;
    return this;
  }

  public LogChoiceBuilder setRemote(boolean remote) {
    this.remote = remote;
    return this;
  }

  public LogChoiceBuilder setTitle(String title) {
    this.title = title;
    return this;
  }

  public LogChoiceBuilder setSelected(boolean selected) {
    this.selected = selected;
    return this;
  }

  public LogChoiceBuilder setUid(String uid) {
    this.uid = uid;
    return this;
  }

  public LogChoiceBuilder setIncludes(List<CompositeInclusion> includes) {
    this.includes = includes;
    return this;
  }

  public LogChoice createLogChoice() {
    assert hasText(group) : "Group must be specified";
    assert hasText(path) : "Path must be specified";
    assert hasText(node) : "Node must be specified";
    assert hasText(title) : "Title must be specified";
    return new LogChoice(group, convertToUnixStyle(path, true), node, remote, title, selected, uid, includes);
  }
}