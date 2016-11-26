package ru.ftc.upc.testing.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ftc.upc.testing.analog.model.ChoiceComponents;

/**
 * @author Toparvion
 */
public final class Util {
  private static final Logger log = LoggerFactory.getLogger(Util.class);

  public static final String DEFAULT_TITLE_FORMAT = "$f ($g)";
  public static final String DEFAULT_ENCODING = "UTF-8";

  static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
  }

  public static ChoiceComponents extractChoiceComponents(String path) {
    String[] entryTokens = path.split("(?i)\\x20as\\x20");
    if (entryTokens.length > 2) {
      log.error("The following log path entry is malformed (contains {} ' as ' tokens " +
              "but must contain no more than 1) and therefore will be ignored:\n{}", entryTokens.length, path);
      return null;
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
      pureTitle = DEFAULT_TITLE_FORMAT;
      selectedByDefault = !path.equals(purePath);
    }

    return new ChoiceComponents(purePath, pureTitle, selectedByDefault);
  }

  public static String expandTitle(String purePath, String pureTitle, String groupName) {
    String fileName = extractFileName(purePath);
    return pureTitle.replaceAll("(?i)\\$f", fileName)
                    .replaceAll("(?i)\\$g", groupName)
                    .replaceAll("(^\")|(\"$)", "");
  }

  /**
   * @return the given encoding name formatted for comparison on frontend
   */
  public static String formatEncodingName(String originalEncodingName) {
    return originalEncodingName.toUpperCase();
  }
}
