package tech.toparvion.analog.util;

import tech.toparvion.analog.model.config.entry.LogPath;
import tech.toparvion.analog.model.config.entry.LogType;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Miscellaneous static methods for working with log paths (both file and custom ones)
 *
 * @author Toparvion
 * @since v0.11
 */
public final class PathUtils {
  /**
   * The combination of characters used to separate log type (a.k.a custom schema) from actual log path (a.k.a target)
   */
  public static final String CUSTOM_SCHEMA_SEPARATOR = "://";

  /**
   * Converts given path to Unix style, e.g. {@code C:\lang\analog} into {@code C:/lang/analog}.
   * @param path             path to convert
   * @return                 converted path
   */
  public static String convertToUnixStyle(String path) {
    // in case of working on Windows the path needs to be formatted to Unix style
    return path.replaceAll("\\\\", "/");
  }

  /**
   * Removes given path's first character if it is forward slash '/' and the path contains a colon ':' (i.e. the path
   * is not ordinary absolute Unix path). In case of two or more slashes at the very beginning of the path, removes
   * reduces them to only one unconditionally.<br/>
   * Such a removal is usually needed for paths taken from some sources where even Windows paths are prepended with '/'.
   * @param path a path to preprocess
   * @return the same path without leading slash (if any)
   */
  public static String removeLeadingSlash(String path) {
    // if for some reason path starts with multiple slashes it must be reduced to single slash before next check
    path = path.replaceAll("^/+", "/");
    return path.startsWith("/") && path.contains(":")
        ? path.substring(1)   // to omit 'artificial' leading slash added for correct handling on frontend
        : path;

  }

  /**
   * Checks if given path points to a file located on current machine's file system, i.e. if it a usual path like
   * {@code /home/user/app.log} or {@code C:/Users/user/app.log} AND NOT a custom schema prefixed path like
   * {@code docker://container} or {@code /node://remote/home/user/app.log}.<p>
   * <strong>CAUTION:</strong> This methods relies on clean paths only in a sense that there MUST NOT be any leading slash
   * (usually came from by web client). So that any path received from the client and looking like
   * {@code /k8s://deploy ...} must be {@linkplain #removeLeadingSlash(String) preprocessed and shorten} to form like
   * {@code k8s://deploy...} (without leading slash).
   * @param path path to check
   * @return {@code false} if given path contains {@link PathUtils#CUSTOM_SCHEMA_SEPARATOR custom schema separator}
   * and {@code true} otherwise
   */
  public static boolean isLocalFilePath(String path) {
    return path.indexOf(CUSTOM_SCHEMA_SEPARATOR, 1) == -1;
  }

  /**
   * Returns a file name denoted by given {@code path}.<br/>
   * If given a custom path, detects the name as substring after the latest forward slash. Consequently, implies that
   * the path has been already {@linkplain #convertToUnixStyle(String) converted} to Unix style.<br/>
   * If given a {@linkplain #isLocalFilePath(String) local file path}, relies on {@link Path} functionality to extract
   * the name.
   * @param path path to extract file name from
   * @return file name denoted by given path
   */
  public static String extractFileName(String path) {
    if (!isLocalFilePath(path)) {
      int lastSlashIndex = path.lastIndexOf('/');
      return path.substring(lastSlashIndex+1);

    } else {
      return Paths.get(path)
              .getFileName()
              .toString();
    }
  }

  /**
   * Checks if given path is of type {@link LogType#NODE NODE} and, if so, returns only the local file system path.
   * For example, turns {@code node://north/home/upc/some.log} into {@code /home/upc/some.log}. <br/>
   * Returns path as is for all other log types.
   * @param logPath path to extract local path from
   * @return local file system path
   */
  public static String extractLocalPath(LogPath logPath) {
    if (logPath.getType() != LogType.NODE) {
      return logPath.getFullPath();
    }
    String logFullPath = logPath.getFullPath();
    int separatorIdx = logFullPath.indexOf(CUSTOM_SCHEMA_SEPARATOR);
    int nodeSlashIdx = logFullPath.indexOf('/', separatorIdx + CUSTOM_SCHEMA_SEPARATOR.length());
    return logFullPath.substring(nodeSlashIdx);
  }
}
