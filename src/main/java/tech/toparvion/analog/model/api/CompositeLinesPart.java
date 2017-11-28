package tech.toparvion.analog.model.api;

import java.util.List;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")     // getters are used during serialization to JSON
public class CompositeLinesPart extends LinesPart {
  private final String sourceNode;
  private final String sourcePath;
  private final long timestamp;

  public CompositeLinesPart(List<StyledLine> lines, String sourceNode, String sourcePath, long timestamp) {
    super(lines);
    this.sourceNode = sourceNode;
    this.sourcePath = sourcePath;
    this.timestamp = timestamp;
  }

  public String getSourceNode() {
    return sourceNode;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
