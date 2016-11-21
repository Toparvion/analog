package ru.ftc.upc.testing.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ftc.upc.testing.analog.model.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.LogChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Toparvion
 */
public final class Util {
  private static final Logger log = LoggerFactory.getLogger(Util.class);

  static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
  }

  public static Stream<LogChoice> flattenGroup(ChoiceGroup group) {
    List<LogChoice> choices = new ArrayList<>();
    for (String path : group.getPaths()) {

      String[] entryTokens = path.split("(?i)\\x20as\\x20");
      if (entryTokens.length > 2) {
        log.error("The following log path entry is malformed (contains {} ' as ' tokens " +
                "but must contain no more than 1) and therefore will be ignored:\n{}", entryTokens.length, path);
        continue;
      }

      String purePath, pureTitle;
      boolean selectedByDefault;
      if (entryTokens.length > 1) {
        purePath = entryTokens[0].trim();
        String origTitle = entryTokens[1];
        pureTitle = origTitle.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
        selectedByDefault = !origTitle.equals(pureTitle);

      } else {
        purePath = path.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
        pureTitle = "$f ($g)";
        selectedByDefault = !path.equals(purePath);
      }

      String title = expandTitle(purePath, pureTitle, group.getGroup());
      choices.add(new LogChoice(
                          group.getGroup(),
                          group.getPathBase() + purePath,
                          title,
                          selectedByDefault));
    }

    return choices.stream();
  }

  private static String expandTitle(String purePath, String pureTitle, String groupName) {
    String fileName = extractFileName(purePath);
    return pureTitle.replaceAll("(?i)\\$f", fileName)
                    .replaceAll("(?i)\\$g", groupName)
                    .replaceAll("(^\")|(\"$)", "");
  }
}
