package tech.toparvion.analog.service.choice.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.api.LogChoice;
import tech.toparvion.analog.model.api.LogChoiceBuilder;
import tech.toparvion.analog.model.config.entry.LogType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static tech.toparvion.analog.service.choice.LogChoiceUtils.expandTitle;
import static tech.toparvion.analog.util.PathUtils.convertToUnixStyle;

/**
 * A choices provider that scans specified directories for log files. This is usually convenient for local AnaLog
 * deployments when the user just want to point AnaLog to some directory and have all its files in the choices list.
 * @author Toparvion
 */
@Service
public class FileSystemScanner {
  private static final Logger log = LoggerFactory.getLogger(FileSystemScanner.class);

  /**
   * Scans the specified directories for files and adds them into resulting set without duplicates. The title for each
   * found log choice is composed as {@code $f ($g)} where {@code $f} is file name (with extension but without the
   * whole path) and {@code $g} is given group name.
   * @param directories a list of string representations of paths to directories for scanning; can be either absolute
   *                   or relative ones to the current working directory
   * @param groupName the name of the group to include in each choice's title
   * @return a duplicate-free set of log choices to include into resulting set for the client
   */
  public Set<LogChoice> scanForChoices(List<String> directories, String groupName) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    for (String dir : directories) {
      Path scanDirPath = Paths.get(dir);
      if (!Files.isDirectory(scanDirPath)) {
        log.warn("Path '{}' is not a directory. Won't be scanned for logs.", scanDirPath);
        continue;
      }
      try (Stream<Path> scannedPaths = Files.list(scanDirPath)) {
        choices.addAll(scannedPaths   // such sets merging allows to exclude duplicates while preserving explicit paths
            .filter(Files::isRegularFile)   // the scanning is not recursive so we bypass nested directories
            .map(logPath -> new LogChoiceBuilder()
                .setGroup(groupName)
                .setId(convertToUnixStyle(logPath.toAbsolutePath().toString()))
                .setType(LogType.LOCAL_FILE.toString())
                .setTitle(expandTitle("$f ($g)", logPath.toString(), groupName))
                .createLogChoice())
            .collect(toSet()));

      } catch (IOException e) {
        log.error("Failed to scan directory '{}'; will be ignored.", dir, e);
      }
    }
    return choices;
  }

}
