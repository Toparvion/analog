package ru.ftc.upc.testing.analog.model;

/**
 * @author Toparvion
 */
public class Line {
  private final String text;
  private final String level;


  public Line(String text, String level) {
    this.text = text;
    this.level = level;
  }

  public String getText() {
    return text;
  }

  public String getLevel() {
    return level;
  }

  @Override
  public String toString() {
    return "Line{" +
            "text='" + text + '\'' +
            ", level='" + level + '\'' +
            '}';
  }
}
