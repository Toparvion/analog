package ru.ftc.upc.testing.analog.model;

import java.util.List;

/**
 * @author Toparvion
 */
public class Part {
  private final List<Line> items;

  public Part(List<Line> items) {
    this.items = items;
  }

  public List<Line> getItems() {
    return items;
  }
}
