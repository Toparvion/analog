package ru.ftc.upc.testing.analog.util;

/**
 * @author Toparvion
 */
public abstract class Util {

  public static String extractFileName(String path) {
    int forwardSlashIndex = path.lastIndexOf('/');
    if (forwardSlashIndex != -1) {
      return path.substring(forwardSlashIndex + 1);
    }

    int backwardSlashIndex = path.lastIndexOf('\\');
    if (backwardSlashIndex != -1) {
      return path.substring(backwardSlashIndex + 1);
    }

    return path;
  }
}
