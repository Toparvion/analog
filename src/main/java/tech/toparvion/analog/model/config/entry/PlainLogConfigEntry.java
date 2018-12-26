package tech.toparvion.analog.model.config.entry;

/**
 * @author Toparvion
 * @since v0.11
 */
public class PlainLogConfigEntry extends AbstractLogConfigEntry {

  private LogPath path;

  public PlainLogConfigEntry() { }

  public LogPath getPath() {
    return path;
  }

  public void setPath(LogPath path) {
    this.path = path;
  }

  @Override
  public String getId() {
    // for plain logs the ID is equal to the full path of target log file as it is the only element in this entry
    return path.getFullPath();
  }

  @Override
  public LogType getType() {
    return path.getType();
  }

  @Override
  public String toString() {
    return "PlainLogConfigEntry{" +
        "path=" + path +
        "} " + super.toString();
  }
}
