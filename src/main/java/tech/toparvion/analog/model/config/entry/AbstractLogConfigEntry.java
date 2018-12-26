package tech.toparvion.analog.model.config.entry;

/**
 * @author Toparvion
 * @since v0.11
 */
public abstract class AbstractLogConfigEntry {
  protected boolean selected = false;
  protected String title = "";

  /**
   * @return constant identifier for referencing to this log by the users (e.g. by means of browser bookmarks)
   */
  public abstract String getId();

  public abstract LogType getType();

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public String toString() {
    return "AbstractLogConfigEntry{" +
        "selected=" + selected +
        ", title='" + title + '\'' +
        '}';
  }
}
