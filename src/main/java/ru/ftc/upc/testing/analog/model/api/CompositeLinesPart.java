package ru.ftc.upc.testing.analog.model.api;

import java.util.List;

/**
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")     // getters are used during serialization to JSON
public class CompositeLinesPart extends LinesPart {
  private final String sourceNode;
  private final long timestamp;

  public CompositeLinesPart(List<StyledLine> lines, String sourceNode, long timestamp) {
    super(lines);
    this.sourceNode = sourceNode;
    this.timestamp = timestamp;
  }

  public String getSourceNode() {
    return sourceNode;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
