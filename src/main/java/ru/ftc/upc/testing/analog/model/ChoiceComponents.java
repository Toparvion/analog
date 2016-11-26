package ru.ftc.upc.testing.analog.model;

/**
 * @author Toparvion
 */
public class ChoiceComponents {
  private final String purePath;
  private final String pureTitle;
  private final boolean selectedByDefault;

  public ChoiceComponents(String purePath, String pureTitle, boolean selectedByDefault) {
    this.purePath = purePath;
    this.pureTitle = pureTitle;
    this.selectedByDefault = selectedByDefault;
  }

  public String getPurePath() {
    return purePath;
  }

  public String getPureTitle() {
    return pureTitle;
  }

  public boolean isSelectedByDefault() {
    return selectedByDefault;
  }
}
