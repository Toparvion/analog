package tech.toparvion.analog.remote.agent.origin.restrict;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.config.access.AllowedLogLocations;
import tech.toparvion.analog.model.config.access.FileAllowedLogLocations;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.util.List;

import static java.lang.String.format;

/**
 * Log access guard responsible for restricting access to file logs (only).
 * Acts as a singleton stateless bean basing its behavior on autowired {@linkplain AllowedLogLocations settings} bean.
 * 
 * @author Toparvion
 * @since v0.12
 */
@Service
public class FileAccessGuard {
  private static final Logger log = LoggerFactory.getLogger(FileAccessGuard.class);
  
  private final List<String> includingGlobs;
  private final List<String> excludingGlobs;
  private final int symlinkResolutionLimit;

  @Autowired
  public FileAccessGuard(AllowedLogLocations allLocations) {
    FileAllowedLogLocations fileLocations = allLocations.getFile();
    this.includingGlobs = fileLocations.getInclude();
    this.excludingGlobs = fileLocations.getExclude();
    this.symlinkResolutionLimit = fileLocations.getSymlinkResolutionLimit();
  }

  /**
   * Checks given path against {@linkplain FileAllowedLogLocations configured file log locations} in the following 
   * manner: <ol>
   *   <li>Normalizes given path:<ul> 
   *      <li>turns it from relative path to an absolute one</li>
   *      <li>converts slashes to current OS format</li>
   *      <li>resolves symbolic links as many times as {@code allowed-log-locations.file.symlink-resolution-limit}
   *      property specifies; if the property equals 0, symlink resolution is denied at all (log cannot be read)
   *      </li></ul>
   *   </li>
   *   <li>Checks given path against {@code allowed-log-locations.file.include} config section which contains a 
   *   list of GLOB patterns that the path MUST conform to. The method processes the list in the order of declaration, 
   *   the first matching pattern wins, no other patterns are checked in this case. If no patterns are specified, 
   *   an exception is thrown immediately, without any further checks. If given path conforms to any of including GLOB 
   *   patterns, it doesn't mean that the path is allowed because it must pass the check against excluding patterns 
   *   (see next).</li>
   *   <li>Checks given path against {@code allowed-log-locations.file.exclude} config section which contains a list 
   *   of GLOB patterns that the path MUST NOT conform to. The method processes the list in the order of declaration, 
   *   the first matching pattern wins, no other patterns are checked in this case. If no patterns are specified, 
   *   the path is considered allowed and the method returns normally.     
   *   </li>
   * </ol>
   * @param pathString string representation of the path to check
   * @throws AccessControlException in case of any access violation (including IO errors during the check)
   */
  public void checkAccess(String pathString) throws AccessControlException {
    // first let's check if there is any allowed location
    if (includingGlobs.isEmpty()) {
      throw new AccessControlException("No allowed file log locations specified. See 'allowed-log-locations' property.");
    }
    // then streamline the path in order to avoid tricks with relative paths
    Path path = Paths.get(pathString).toAbsolutePath().normalize();
    // now find out the real log path by resolving all the symlinks towards it
    int hop = 0;
    while (hop < symlinkResolutionLimit && Files.isSymbolicLink(path)) {
      Path targetPath;
      try {
        targetPath = Files.readSymbolicLink(path);
      } catch (IOException e) {
        log.error(format("Failed to resolve symlink '%s'", path), e);
        throw new AccessControlException(format("Unable to check access for symbolic link '%s' because of IO " +
                "exception: %s", path, e.getMessage()));
      }
      log.trace("Symbolic link '{}' resolved to '{}' (hop {} of {}).", path, targetPath, (hop+1), symlinkResolutionLimit);
      path = targetPath;
      hop++;
    }
    if (hop == symlinkResolutionLimit && Files.isSymbolicLink(path)) {
      var message = (symlinkResolutionLimit == 0)
              ? "Symbolic links to logs are not allowed."
              : format("Symbolic links resolution limit (%d) has been reached.", symlinkResolutionLimit);
      throw new AccessControlException(message);
    }
    if (!pathString.equals(path.toString())) {
      log.debug("Path '{}' has been normalized to '{}'.", pathString, path.toString());
    }
    // now it's time to check the path against including patterns
    log.trace("Checking log path '{}' against {} INCLUDING pattern(s)...", path, includingGlobs.size());
    boolean anyMatch = false;
    for (String includingGlob : includingGlobs) {
      var includingMatcher = FileSystems.getDefault().getPathMatcher("glob:" + includingGlob);
      if (includingMatcher.matches(path)) {
        anyMatch = true;
        log.info("Log path '{}' is allowed according to '{}' including pattern. Will be also checked for exclusion.",
                pathString, includingGlob);
      }
    }
    if (!anyMatch) {
      throw new AccessControlException(format("Access denied: log path '%s' is not included into " +
              "'allowed-log-locations' property.", pathString));
    }
    // and last, we must check the path against excluding patterns (if any)
    if (!excludingGlobs.isEmpty()) {
      log.trace("Checking log path '{}' against {} EXCLUDING pattern(s)...", pathString, excludingGlobs.size());
      for (String excludingGlob : excludingGlobs) {
        var excludingMatcher = FileSystems.getDefault().getPathMatcher("glob:" + excludingGlob);
        if (excludingMatcher.matches(path)) {
          log.info("Log path '{}' is denied according to excluding pattern: {}", pathString, excludingGlob);
          throw new AccessControlException(format("Access denied: log path '%s' is excluded from " +
                  "'allowed-log-locations' property.", pathString));
        }
      }
    }
    log.debug("All checks passed. Access is allowed for log path: {}", pathString);
  }
  
}
