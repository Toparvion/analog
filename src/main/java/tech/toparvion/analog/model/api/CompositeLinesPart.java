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
  private final String highlightColor;

  public CompositeLinesPart(List<StyledLine> lines,
                            String sourceNode,
                            String sourcePath,
                            long timestamp,
                            String highlightColor) {
    super(lines);
    this.sourceNode = sourceNode;
    this.sourcePath = sourcePath;
    this.timestamp = timestamp;
    this.highlightColor = highlightColor;
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

  public String getHighlightColor() {
    return highlightColor;
  }
}
