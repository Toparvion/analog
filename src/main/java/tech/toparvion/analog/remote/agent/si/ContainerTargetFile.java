package tech.toparvion.analog.remote.agent.si;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author Toparvion
 * @since v0.11
 */
public class ContainerTargetFile extends File {
  /**
   * The prefix including {@value tech.toparvion.analog.util.PathUtils#CUSTOM_SCHEMA_SEPARATOR} separator.
   */
  private final String fullPrefix;
  /**
   * The target resource for tailing, e.g. {@code pod/myapp-666568ff7f-zmf88}
   */
  private final String target;
  /**
   * The full log path as it came from the client. May differ from the {@code fullPrefix+target} combination in case 
   * of complicated Kubernetes targets. For example, if the original log path was specified as<br/>   
   * {@code k8s://namespace/upc/pod/myapp-666568ff7f-zmf88} <br/>
   * then the target will be just <br/>
   * {@code pod/myapp-666568ff7f-zmf88}.
   */
  @Nullable
  private final String originalLogPath;

  public ContainerTargetFile(String fullPrefix, String target, @Nullable String originalLogPath) {
    super("");
    this.fullPrefix = fullPrefix;
    this.target = target;
    this.originalLogPath = originalLogPath;
  }

  public ContainerTargetFile(String fullPrefix, String target) {
    this(fullPrefix, target, null);
  }

  /**
   * For container target files the name is just a resource ID, for example {@code deploy/my-deployment} for K8s or
   * {@code my-container} for Docker
   * @return target resource (same as {@link #getCanonicalPath()})
   */
  @Nonnull
  @Override
  public String getName() {
    return target;
  }

  /**
   * For container target files the canonical path is just a resource ID,
   * for example {@code deploy/my-deployment} for K8s or {@code my-container} for Docker
   * @return target resource (same as {@link #getName()})
   */
  @Nonnull
  @Override
  public String getCanonicalPath() {
    return target;
  }

  @Nonnull
  @Override
  public String getAbsolutePath() {
    return (originalLogPath != null)
            ? originalLogPath
            : (fullPrefix + target);
  }
}
