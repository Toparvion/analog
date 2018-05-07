package tech.toparvion.analog.model.api;

/**
 * A part of composite log, i.e. a file (defined by node and path) that included into composite log among others files
 *
 * @author Toparvion
 * @since v0.10
 */
public class CompositeInclusion {
  private final String node;
  private final String path;

  public CompositeInclusion(String node, String path) {
    this.node = node;
    this.path = path;
  }

  public String getNode() {
    return node;
  }

  public String getPath() {
    return path;
  }

  @Override
  public String toString() {
    return "CompositeInclusion{" +
        "node='" + node + '\'' +
        ", path='" + path + '\'' +
        '}';
  }
}
