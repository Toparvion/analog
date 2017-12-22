package tech.toparvion.analog.util;

/**
 * @author Toparvion
 */
public final class Util {

  public static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
  }

  public static String nvls(String s, String def) {
    return (s == null || "".equals(s))
            ? def
            : s;
  }
}
