package tech.toparvion.analog.remote.agent.origin.restrict;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.toparvion.analog.model.config.access.AllowedLogLocations;
import tech.toparvion.analog.model.config.access.FileAllowedLogLocations;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.List;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Toparvion
 * @since v0.12
 * @implNote <em>ATTENTION!</em> On Windows the JVM running this test must be started with Administrator privileges 
 * because otherwise it would fail to create symbolic links on the file system! 
 */
class FileAccessGuardTest {

  private static final String ERROR_LOG_STRING_PATH = "/home/me/app1/error.log";
  private static final String SYMLINK_STRING_PATH = "/home/me/app1/symlink-to-error.log";
  private static final String SYMLINK_2_SYMLINK_STRING_PATH = "/home/me/app1/symlink-to-symlink-to-error.log";
  private static final String HACKING_SYMLINK_STRING_PATH = "/home/me/app1/hacking-symlink.log";

  private static String basePath;
  private AllowedLogLocations locations;

  @BeforeAll
  static void prepare() throws URISyntaxException, IOException {
    // 1. First let's find out the absolute path the test is running on. It is needed for further paths resolution. 
    final var anchorRelativePath = "restrict/anchor.txt";
    URL url = Thread.currentThread().getContextClassLoader().getResource(anchorRelativePath);
    assertNotNull(url);
    Path anchorPath = Paths.get(url.toURI());
    assertTrue(Files.exists(anchorPath, NOFOLLOW_LINKS));
    basePath = anchorPath.getParent().toAbsolutePath().toString();
    assertTrue(basePath.endsWith(System.getProperty("file.separator") + "restrict"));

    // 2. We have to create symlink by hand because there is no guarantee that it neither will survive in repository 
    // nor won't be lost during tests' artifacts deployment 
    var target = Paths.get(based(ERROR_LOG_STRING_PATH));
    var link = Paths.get(based(SYMLINK_STRING_PATH));
    Files.createSymbolicLink(link, target);
    var link2link = Paths.get(based(SYMLINK_2_SYMLINK_STRING_PATH));
    Files.createSymbolicLink(link2link, link);
    var hackingSymlink = Paths.get(based(HACKING_SYMLINK_STRING_PATH));
    var hackingTarget = Paths.get(based("/etc/passwd"));
    Files.createSymbolicLink(hackingSymlink, hackingTarget);
  }

  @Test
  @DisplayName("Default glob pattern allows for *.log files in home directory only")
  void simplestCheck() {
    var logLocations = composeLocations("/home/me/app1/*.log");
    var sut = new FileAccessGuard(logLocations);
    assertDoesNotThrow(() -> sut.checkAccess(based(ERROR_LOG_STRING_PATH)));
  }

  @Test
  @DisplayName("A path that doesn't conform to any of 2 inclusions, causes exception")
  void twoIncompatibleInclusions() {
    var logLocations = composeLocations("/home/me/app1/*.log", "/home/me/**/log/**");
    var sut = new FileAccessGuard(logLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based("/home/me/app1/readme.txt")));
    assertTrue(accessException.getMessage().startsWith("Access denied"));
    assertTrue(accessException.getMessage().contains("is not included into"));
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  @DisplayName("Empty inclusions list causes an exception (without accounting for exclusions)")
  void emptyInclusionsList() {
    var logLocations = composeLocations();
    var sut = new FileAccessGuard(logLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based(ERROR_LOG_STRING_PATH)));
    assertTrue(accessException.getMessage().startsWith("No allowed file log locations specified"));
    var fileAllowedLogLocations = locations.getFile();
    // the only invocation should take place in constructor 
    verify(fileAllowedLogLocations, times(1)).getExclude();
  }

  @Test
  @DisplayName("A file conforming to excluding pattern cannot be accessed")
  void testExclusion() {
    var logLocations = composeLocations("/home/me/app1/*.log");
    setExclusions(logLocations, "/**/audit.log");
    var sut = new FileAccessGuard(logLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based("/home/me/app1/audit.log")));
    assertTrue(accessException.getMessage().startsWith("Access denied"));
    assertTrue(accessException.getMessage().contains("is excluded from"));
  }

  @Test
  @DisplayName("Logs cannot be referenced via symlinks if symlink handling is disabled")
  void prohibitedLinks() {
    var allLocations = composeLocations("/home/me/app1/*.log");
    var fileLocations = allLocations.getFile();
    fileLocations.setSymlinkResolutionLimit(0);     // to prohibit following the links
    var sut = new FileAccessGuard(allLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based(SYMLINK_STRING_PATH)));
    assertTrue(accessException.getMessage().startsWith("Symbolic links to logs are not allowed"));
  }

  @Test
  @DisplayName("A log can be referenced via symlink (if allowed)")
  void symlinksAreSupported() {
    var allLocations = composeLocations("/home/me/app1/*.log");
    var fileLocations = allLocations.getFile();
    fileLocations.setSymlinkResolutionLimit(1);     // to allow at max 1 hop through links
    var sut = new FileAccessGuard(allLocations);
    assertDoesNotThrow(() -> sut.checkAccess(based(SYMLINK_STRING_PATH)));
  }

  @Test
  @DisplayName("Number of link resolutions can be restricted")
  void linksFollowingRestriction() {
    var allLocations = composeLocations("/home/me/app1/*.log");
    var fileLocations = allLocations.getFile();
    fileLocations.setSymlinkResolutionLimit(1);     // to allow at max 1 hop through links
    var sut = new FileAccessGuard(allLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based(SYMLINK_2_SYMLINK_STRING_PATH)));
    assertTrue(accessException.getMessage().startsWith("Symbolic links resolution limit"));
  }

  @Test
  @DisplayName("Symlink within allowed location does not provide a way to get a log from disallowed location")
  void hackingSymlinkHandling() {
    var allLocations = composeLocations("/home/me/app1/*.log");
    var fileLocations = allLocations.getFile();
    fileLocations.setSymlinkResolutionLimit(1);     // to allow at max 1 hop through links
    var sut = new FileAccessGuard(allLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based(HACKING_SYMLINK_STRING_PATH)));
    assertTrue(accessException.getMessage().startsWith("Access denied"));
    assertTrue(accessException.getMessage().contains("is not included into"));
  }
  
  @Test
  @DisplayName("Relative path doesn't provide a way to get a log out of restricted locations")
  void relativePaths() {
    var allLocations = composeLocations("/home/me/app1/*.log");
    var sut = new FileAccessGuard(allLocations);
    var accessException = assertThrows(AccessControlException.class,
            () -> sut.checkAccess(based("/../restrict/etc/passwd")));
    assertTrue(accessException.getMessage().startsWith("Access denied"));
    assertTrue(accessException.getMessage().contains("is not included into"));
  }
  
  @Test
  @DisplayName("Test harness self-check: log locations are created correctly")
  void testIncludeGlobComposition() {
    var includeGlob = "/home/*.log";
    AllowedLogLocations locations = composeLocations(includeGlob);
    assertTrue(locations.getFile().getInclude().size() > 0);
  }

  private static String based(String pathTail) {
    return basePath + pathTail;
  }

  private AllowedLogLocations composeLocations(String... includeGlobs) {
    List<String> absoluteGlobs = Arrays.stream(includeGlobs)
            .map(glob -> basePath + glob)
            .collect(toList());
    var fileLocations = spy(new FileAllowedLogLocations());
    fileLocations.setInclude(absoluteGlobs);
    locations = new AllowedLogLocations();
    locations.setFile(fileLocations);
    return locations;
  }

  private void setExclusions(AllowedLogLocations inclusions, String... exclusions) {
    List<String> absoluteGlobs = Arrays.stream(exclusions)
            .map(glob -> basePath + glob)
            .collect(toList());
    inclusions.getFile().setExclude(absoluteGlobs);
  }

  @AfterAll
  static void windUp() throws IOException {
    Files.delete(Paths.get(based(SYMLINK_2_SYMLINK_STRING_PATH)));
    Files.delete(Paths.get(based(SYMLINK_STRING_PATH)));
    Files.delete(Paths.get(based(HACKING_SYMLINK_STRING_PATH)));
  }
}