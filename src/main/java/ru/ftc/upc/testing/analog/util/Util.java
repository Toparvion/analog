package ru.ftc.upc.testing.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Toparvion
 */
public final class Util {
  private static final Logger log = LoggerFactory.getLogger(Util.class);

  public static final String DEFAULT_TITLE_FORMAT = "$f ($g)";

  public static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
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

  public static String nvls(String s, String def) {
    return (s == null || "".equals(s))
            ? def
            : s;
  }
}
