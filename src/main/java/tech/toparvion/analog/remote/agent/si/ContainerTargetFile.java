package tech.toparvion.analog.remote.agent.si;

import java.io.File;

/**
 * @author Plizga
 * @since v0.11
 */
@SuppressWarnings("NullableProblems")
public class ContainerTargetFile extends File {
  /**
   * The prefix including {@link tech.toparvion.analog.util.PathUtils#CUSTOM_SCHEMA_SEPARATOR CUSTOM_SCHEMA_SEPARATOR}.
   */
  private final String fullPrefix;
  private final String target;

  public ContainerTargetFile(String fullPrefix, String target) {
    super("");
    this.fullPrefix = fullPrefix;
    this.target = target;
  }

  @Override
  public String getName() {
    return target;
  }

  @Override
  public String getAbsolutePath() {
    return fullPrefix + target;
  }
}
