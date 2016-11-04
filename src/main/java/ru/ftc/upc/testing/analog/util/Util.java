package ru.ftc.upc.testing.analog.util;

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
}
