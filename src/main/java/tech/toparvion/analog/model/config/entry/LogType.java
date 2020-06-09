package tech.toparvion.analog.model.config.entry;

import org.springframework.util.Assert;
import tech.toparvion.analog.util.PathUtils;

import javax.annotation.Nullable;

/**
 * @author Toparvion
 * @since v0.11
 */
public enum LogType {
  LOCAL_FILE(""),
  NODE("node"),
  COMPOSITE("composite"),
  DOCKER("docker"),
  KUBERNETES("kubernetes"),
  K8S("k8s", KUBERNETES)
  ;

  private final String prefix;
  @Nullable
  private final LogType aliasFor;

  LogType(String prefix) {
    this(prefix, null);
  }

  LogType(String prefix, @Nullable LogType aliasFor) {
    this.prefix = prefix;
    this.aliasFor = aliasFor;
  }

  public String getPrefix() {
    return prefix;
  }

  /**
   * Checks if given log path is of current log type, e.g. whether {@code docker://my-container} is
   * of type {@link #DOCKER}.<br/>
   * The checking takes aliases in count therefore both {@link #KUBERNETES} and {@link #K8S} types match
   * path {@code k8s://my-deploy/my-pod}.<br/>
   * The checking is case <em>in</em>sensitive.
   * @param logPath a path to check
   * @return {@code true} if current log type matches the path
   */
  public boolean matches(String logPath) {
    Assert.hasText(logPath, "logPath must not be empty");
    if (this == LOCAL_FILE) { // the only special case is local file as it doesn't contain any prefix, so check it first
      return PathUtils.isLocalFilePath(logPath);
    }
    if (logPath.toLowerCase().startsWith(prefix + PathUtils.CUSTOM_SCHEMA_SEPARATOR)) {
      return true;
    }
    if (aliasFor == null) {
      return false;
    }
    return aliasFor.matches(logPath);
  }

  public static LogType detectFor(String logPath) {
    for (LogType type : values()) {
      if (type.matches(logPath)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No matching type found for logPath=" + logPath);
  }

  @Override
  public String toString() {
    return name();                // to explicitly denote the desired output format
  }
}
