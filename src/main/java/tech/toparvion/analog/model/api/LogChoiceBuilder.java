package tech.toparvion.analog.model.api;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import tech.toparvion.analog.model.config.entry.LogType;

import java.util.List;

/**
 * Helper class aimed to simplify log choice instances construction
 *
 * @author Toparvion
 * @since v0.10
 */
public class LogChoiceBuilder {
  private String group;
  private String id;
  private String type;        // corresponds to LogType#name()
  private String title;
  private boolean selected = false;

  private String node;
  private List<String> includes = List.of();

  public LogChoiceBuilder setGroup(String group) {
    this.group = group;
    return this;
  }

  public LogChoiceBuilder setId(String id) {
    this.id = id;
    return this;
  }

  public LogChoiceBuilder setType(String type) {
    this.type = type;
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

  public LogChoiceBuilder setNode(String node) {
    this.node = node;
    return this;
  }

  public LogChoiceBuilder setIncludes(List<String> includes) {
    this.includes = includes;
    return this;
  }

  public LogChoice createLogChoice() {
    Assert.hasText(group, "Group must be specified");
    Assert.hasText(id, "Log ID must be specified");
    Assert.hasText(type, "Type must be specified");
    Assert.hasText(title, "Title must be specified");
    if (type.equals(LogType.NODE.getPrefix())) {
      Assert.state(StringUtils.hasText(node),
          "Node must be specified for 'node://' type log, id="+id);
    }
    if (type.equals(LogType.COMPOSITE.getPrefix())) {
      Assert.state(!includes.isEmpty(),
          "Inclusions must be specified for 'composite://' type log, id="+id);
    }
    return new LogChoice(group, id, type, title, selected, node, includes);
  }
}