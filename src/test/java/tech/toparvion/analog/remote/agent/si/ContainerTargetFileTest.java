package tech.toparvion.analog.remote.agent.si;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Toparvion
 * @since v0.14
 */
class ContainerTargetFileTest {

  private static final String K8S_FULL_PREFIX = "k8s://";
  private static final String K8S_TARGET = "deploy/my-deploy";

  @Test
  @DisplayName("When originalPath isn't set, the absolutePath is 'prefix+target'")
  void fullNameWithoutOriginalPath() {
    var sut = new ContainerTargetFile(K8S_FULL_PREFIX, K8S_TARGET);
    assertAll(
        () -> assertEquals(K8S_TARGET, sut.getCanonicalPath()),
        () -> assertEquals(K8S_TARGET, sut.getName()),
        () -> assertEquals((K8S_FULL_PREFIX + K8S_TARGET), sut.getAbsolutePath())
    );
  }

  @Test
  @DisplayName("When originalPath is set, the absolutePath equals to it")
  void fullNameWithOriginalPath() {
    var originalPath = K8S_FULL_PREFIX + "namespace/test/" + K8S_TARGET;
    var sut = new ContainerTargetFile(K8S_FULL_PREFIX, K8S_TARGET, originalPath);
    assertAll(
        () -> assertEquals(K8S_TARGET, sut.getCanonicalPath()),
        () -> assertEquals(K8S_TARGET, sut.getName()),
        () -> assertEquals(originalPath, sut.getAbsolutePath())
    );
  }

  @Test
  @DisplayName("Abstract pathName stays empty with any kind of constructor")
  void actualPathIsEmpty() {
    var sut1 = new ContainerTargetFile(K8S_FULL_PREFIX, K8S_TARGET);
    var sut2 = new ContainerTargetFile(K8S_FULL_PREFIX, K8S_TARGET, "whatever");
    assertEquals("", sut1.getPath());
    assertEquals("", sut2.getPath());
  }
}