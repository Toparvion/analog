package tech.toparvion.analog.remote.agent.si;

import java.io.File;

/**
 * @author Toparvion
 * @since v0.11
 */
@SuppressWarnings("NullableProblems")
public class ContainerTargetFile extends File {
  /**
   * The prefix including {@value tech.toparvion.analog.util.PathUtils#CUSTOM_SCHEMA_SEPARATOR} separator.
   */
  private final String fullPrefix;
  private final String target;

  public ContainerTargetFile(String fullPrefix, String target) {
    super("");
    this.fullPrefix = fullPrefix;
    this.target = target;
  }

  /**
   * For container target files the name is just a resource ID, for example {@code deploy/my-deployment} for K8s or
   * {@code my-container} for Docker
   * @return target resource (same as {@link #getCanonicalPath()})
   */
  @Override
  public String getName() {
    return target;
  }

  /**
   * For container target files the canonical path is just a resource ID,
   * for example {@code deploy/my-deployment} for K8s or {@code my-container} for Docker
   * @return target resource (same as {@link #getName()})
   */
  @Override
  public String getCanonicalPath() {
    return target;
  }

  @Override
  public String getAbsolutePath() {
    return fullPrefix + target;
  }
}
