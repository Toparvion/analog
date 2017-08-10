package ru.ftc.upc.testing.analog.model.api;

import java.util.List;

/**
 * @author Toparvion
 */
public class LinesPart {
  private final List<StyledLine> lines;

  public LinesPart(List<StyledLine> lines) {
    this.lines = lines;
  }

  @SuppressWarnings("unused")     // used by JSON serialization routines
  public List<StyledLine> getLines() {
    return lines;
  }

}
