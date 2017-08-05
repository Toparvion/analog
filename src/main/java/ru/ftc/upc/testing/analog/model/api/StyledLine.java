package ru.ftc.upc.testing.analog.model.api;

/**
 * @author Toparvion
 */
public class StyledLine {
  private final String text;
  private final String style;

  public StyledLine(String text, String style) {
    this.text = text;
    this.style = style;
  }

  public String getText() {
    return text;
  }

  public String getStyle() {
    return style;
  }

  @Override
  public String toString() {
    return "StyledLine{" +
        "style='" + style + '\'' +
        ", text='" + text + '\'' +
        '}';
  }
}
