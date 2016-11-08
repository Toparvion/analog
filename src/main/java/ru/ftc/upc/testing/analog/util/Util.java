package ru.ftc.upc.testing.analog.util;

import ru.ftc.upc.testing.analog.model.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.LogChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Toparvion
 */
public abstract class Util {

  public static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
  }

  public static Stream<LogChoice> flattenGroup(ChoiceGroup group) {
    List<LogChoice> choices = new ArrayList<>();
    for (String path : group.getPaths()) {
      String purePath = path.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
      boolean selectedByDefault = !path.equals(purePath);
      String fileName = extractFileName(purePath);
      choices.add(new LogChoice(group.getGroup(), purePath, fileName, selectedByDefault));
    }

    return choices.stream();
  }
}
