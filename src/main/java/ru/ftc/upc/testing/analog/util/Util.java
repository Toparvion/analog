package ru.ftc.upc.testing.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ftc.upc.testing.analog.model.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.LogChoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

/**
 * @author Toparvion
 */
public final class Util {
  private static final Logger log = LoggerFactory.getLogger(Util.class);

  private static final String DEFAULT_TITLE_FORMAT = "$f ($g)";
  private static final String DEFAULT_ENCODING = "UTF-8";

  static String extractFileName(String path) {
    int lastSlashPosition = Math.max(
            path.lastIndexOf('/'),
            path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
  }

  public static Stream<LogChoice> flattenGroup(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    String encoding = formatEncodingName((group.getEncoding() != null)
                                              ? group.getEncoding()
                                              : DEFAULT_ENCODING);

    // first let's traverse and process all of the path entries as they are commonly used in groups
    for (String path : group.getPaths()) {
      String[] entryTokens = path.split("(?i)\\x20as\\x20");
      if (entryTokens.length > 2) {
        log.error("The following log path entry is malformed (contains {} ' as ' tokens " +
                "but must contain no more than 1) and therefore will be ignored:\n{}", entryTokens.length, path);
        continue;
      }

      String purePath, pureTitle;
      boolean selectedByDefault;
      if (entryTokens.length > 1) {
        purePath = entryTokens[0].trim();
        String origTitle = entryTokens[1];
        pureTitle = origTitle.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
        selectedByDefault = !origTitle.equals(pureTitle);

      } else {
        purePath = path.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
        pureTitle = DEFAULT_TITLE_FORMAT;
        selectedByDefault = !path.equals(purePath);
      }

      String title = expandTitle(purePath, pureTitle, groupName);
      choices.add(new LogChoice(groupName,
                                group.getPathBase() + purePath,
                                encoding, title,
                                selectedByDefault));
    }

    // then let's add scanned directory logs to set being composed
    if (group.getScanDir() != null) {
      Path scanDirPath = Paths.get(group.getScanDir());
      try (Stream<Path> scannedPaths = Files.list(scanDirPath)) {
        choices.addAll(scannedPaths
                        .filter(Files::isRegularFile)   // the scanning is not recursive so we bypass nested directories
                        .map(logPath -> new LogChoice(groupName,
                                                      logPath.toAbsolutePath().toString(),
                                                      encoding,
                                                      expandTitle(logPath.toString(), DEFAULT_TITLE_FORMAT, groupName),
                                                      false))
                .collect(toSet()));
      } catch (IOException e) {
        log.error(format("Failed to scan directory '%s'$ will be ignored", group.getScanDir()), e);
      }
    }

    return choices.stream();
  }

  private static String expandTitle(String purePath, String pureTitle, String groupName) {
    String fileName = extractFileName(purePath);
    return pureTitle.replaceAll("(?i)\\$f", fileName)
                    .replaceAll("(?i)\\$g", groupName)
                    .replaceAll("(^\")|(\"$)", "");
  }

  /**
   * @return the given encoding name formatted for comparison on frontend
   */
  private static String formatEncodingName(String originalEncodingName) {
    return originalEncodingName.toLowerCase().replaceAll("-", "");
  }
}
