package tech.toparvion.analog.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.config.ChoiceGroup;
import tech.toparvion.analog.model.config.entry.AbstractLogConfigEntry;
import tech.toparvion.analog.model.config.entry.PlainLogConfigEntry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.model.config.entry.LogType.LOCAL_FILE;

/**
 * @author Toparvion
 * @since v0.11
 */
public final class ChoiceValidator {
  private static final Logger log = LoggerFactory.getLogger(ChoiceValidator.class);

  /**
   * Traverses given choices, finds all plain config entries which (1) point to local files and (2) have relative path.
   * Then tries to resolve those paths against {@code plainLogsLocalBase} specified for containing group (if any) and
   * replaces the original path of each entry with resolved one.<br/>
   * This is necessary to simplify further logic of log config entry processing.
   * @param choices AnaLog static configuration to process
   */
  public static void applyPathBase(List<ChoiceGroup> choices) {
    for (ChoiceGroup group : choices) {
      String pathBase = group.getPlainLogsLocalBase();
      if (!hasText(pathBase)) {
        continue;
      }
      Path base = Paths.get(pathBase);
      Assert.isTrue(base.isAbsolute(), format("'plainLogsLocalBase' parameter %s is not absolute", pathBase));
      for (PlainLogConfigEntry entry : group.getPlainLogs()) {
        if (entry.getType() != LOCAL_FILE) {
          continue;
        }
        Path entryPath = Paths.get(entry.getPath().getFullPath());
        if (entryPath.isAbsolute()) {
          log.debug("Log path '{}' is already absolute and thus won't be prepended with base.", entryPath);
          continue;
        }
        Path absoluteEntryPath = base.resolve(entryPath).toAbsolutePath();
        String absoluteEntryPathString = absoluteEntryPath.toString();
        entry.getPath().setFullPath(absoluteEntryPathString);
        entry.getPath().setTarget(absoluteEntryPathString);     // for local file logs the target is equal to full path
        log.debug("Changed log config entry's path from '{}' to '{}'.", entryPath, absoluteEntryPathString);
      }
    }
  }

  /**
   * Traverses all the choice groups in order to check whether any entry has 'selected' flag set. If there is no such
   * entries, finds the first one and rises its 'selected' flag. This is necessary to provide the UI with an entry
   * that must be chosen and highlighted as selected by default during the first opening of AnaLog web page.
   * @param choices AnaLog static configuration to check and fix the selected flag
   */
  public static void checkAndFixSelectedEntry(List<ChoiceGroup> choices) {
    if (choices.isEmpty()) {
      return;
    }
    List<AbstractLogConfigEntry> selectedEntries = choices.stream()
        .flatMap(group -> Stream.concat(group.getPlainLogs().stream(),
            group.getCompositeLogs().stream()))
        .filter(AbstractLogConfigEntry::isSelected)
        .collect(toList());
    if (!selectedEntries.isEmpty()) {
      if (selectedEntries.size() > 1) {
        log.warn("There are {} selected entries in configuration: {}\nOnly the first of them will be selected actually.",
            selectedEntries.size(), selectedEntries);
      } else {
        log.debug("Found config entry selected by default: {}", selectedEntries.get(0));
      }
      return;
    }
    ChoiceGroup firstGroup = choices.get(0);
    AbstractLogConfigEntry firstEntry;
    if (!firstGroup.getCompositeLogs().isEmpty()) {
      firstEntry = firstGroup.getCompositeLogs().get(0);

    } else if (!firstGroup.getPlainLogs().isEmpty()) {
      firstEntry = firstGroup.getPlainLogs().get(0);

    } else {
      // TODO process 'scanLocations' section
      firstEntry = new PlainLogConfigEntry();
    }

    firstEntry.setSelected(true);
    log.info("No selected entry specified in configuration. The following entry will be selected by default: {}", firstEntry);
  }
}
