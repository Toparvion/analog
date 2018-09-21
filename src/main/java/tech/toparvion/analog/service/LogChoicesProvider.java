package tech.toparvion.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.model.api.CompositeInclusion;
import tech.toparvion.analog.model.api.LogChoice;
import tech.toparvion.analog.model.api.LogChoiceBuilder;
import tech.toparvion.analog.model.config.ChoiceGroup;
import tech.toparvion.analog.model.config.ChoiceProperties;
import tech.toparvion.analog.model.config.ClusterProperties;
import tech.toparvion.analog.model.config.LogConfigEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static tech.toparvion.analog.util.AnaLogUtils.*;

/**
 * @author Toparvion
 * @since v0.7
 */
@Component
public class LogChoicesProvider {
  private static final Logger log = LoggerFactory.getLogger(LogChoicesProvider.class);
  private static final String DEFAULT_TITLE_FORMAT = "$f ($g)";

  private final List<ChoiceGroup> choices;
  private final String myselfNodeName;

  @Autowired
  public LogChoicesProvider(ChoiceProperties choiceProperties,
                            ClusterProperties clusterProperties) {
    this.choices = choiceProperties.getChoices();
    this.myselfNodeName = clusterProperties.getMyselfNode().getName();
  }

  public List<LogChoice> provideLogChoices() {
    return choices.stream()
        .flatMap(this::flattenGroup)
        .collect(toList());
    // Perhaps it's worth to cache the result here as it cannot change over time (during one application session) but
    // can be potentially asked frequently.
  }

  private Stream<LogChoice> flattenGroup(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    choices.addAll(processCompositeLogs(group));
    choices.addAll(processPlainLogs(group));
    choices.addAll(processScanDir(group));
    return choices.stream();
  }

  private Set<LogChoice> processPlainLogs(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    for (String path : group.getPlainLogs()) {
      ChoiceTokens coms = extractChoiceTokens(path);
      if (coms == null) continue; // the origin of this object is responsible for logging in this case
      String title = expandTitle(coms.getPurePath(), coms.getPureTitle(), groupName);
      // String fullPath = group.getPathBase() + coms.getPurePath();
      String fullPath;
      if (isAbsoluteUri(coms.getPurePath())) {
        fullPath = coms.getPurePath();
      } else {
        Path rawPath = Paths.get(group.getPathBase(), coms.getPurePath());
        Path absPath = rawPath.isAbsolute()
                ? rawPath
                : rawPath.toAbsolutePath();
        fullPath = absPath.toString();
      }
      LogChoice choice = new LogChoiceBuilder()
          .setGroup(groupName)
          .setPath(fullPath)
          .setNode(myselfNodeName)
          .setTitle(title)
          .setSelected(coms.isSelected())
          .createLogChoice();
      choices.add(choice);
    }
    return choices;
  }

  private Set<LogChoice> processCompositeLogs(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    // traverse and process all of the path entries as they are commonly used in groups
    for (LogConfigEntry logConfigEntry : group.getCompositeLogs()) {
      String path = logConfigEntry.getPath();
      String node = nvls(logConfigEntry.getNode(), myselfNodeName);
      String titleFormat = nvls(logConfigEntry.getTitle(), DEFAULT_TITLE_FORMAT);
      String title = expandTitle(path, titleFormat, groupName);
      String fullPath;
      if (isAbsoluteUri(path)) {
        fullPath = path;
      } else {
        Path rawPath = Paths.get(path);
        Path absPath = rawPath.isAbsolute()
                ? rawPath
                : rawPath.toAbsolutePath();
        fullPath = absPath.toString();
      }
      LogChoice choice = new LogChoiceBuilder()
          .setGroup(groupName)
          .setPath(fullPath)
          .setNode(logConfigEntry.getNode())
          .setRemote(!myselfNodeName.equals(node))
          .setTitle(title)
          .setSelected(logConfigEntry.isSelected())
          .setUid(logConfigEntry.getUid())
          .setIncludes(getIncludes(logConfigEntry))
          .createLogChoice();
      choices.add(choice);
    }
    return choices;
  }

  private Set<LogChoice> processScanDir(ChoiceGroup group) {
    // then let's add scanned directory logs to set being composed
    if (group.getScanDir() == null) {
      return emptySet();
    }
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    Path scanDirPath = Paths.get(group.getScanDir());
    try (Stream<Path> scannedPaths = Files.list(scanDirPath)) {
      choices.addAll(scannedPaths   // such sets merging allows to exclude duplicates while preserving explicit paths
          .filter(Files::isRegularFile)   // the scanning is not recursive so we bypass nested directories
          .map(logPath -> new LogChoiceBuilder()
              .setGroup(groupName)
              .setPath(logPath.toAbsolutePath().toString())
              .setNode(myselfNodeName)
              .setTitle(expandTitle(logPath.toString(), DEFAULT_TITLE_FORMAT, groupName))
              .createLogChoice())
          .collect(toSet()));

    } catch (IOException e) {
      log.error(format("Failed to scan directory '%s'; will be ignored.", group.getScanDir()), e);
    }
    return choices;
  }

  private String expandTitle(String purePath, String pureTitle, String groupName) {
    String fileName = extractFileName(purePath);
    return pureTitle.replaceAll("(?i)\\$f", fileName)
        .replaceAll("(?i)\\$g", groupName)
        .replaceAll("(^\")|(\"$)", "");
  }

  private ChoiceTokens extractChoiceTokens(String path) {
    String[] entryTokens = path.split("(?i)\\x20as\\x20");
    if (entryTokens.length > 2) {
      log.error("The following log path entry is malformed (contains {} 'as' tokens " +
          "but must contain no more than 1) and therefore will be ignored:\n{}", entryTokens.length, path);
      return null;
    }

    String purePath, pureTitle;
    boolean selected;
    if (entryTokens.length > 1) {
      purePath = entryTokens[0].trim();
      String origTitle = entryTokens[1];
      pureTitle = origTitle.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
      selected = !origTitle.equals(pureTitle);

    } else {
      purePath = path.replaceAll("(?i)\\x20*\\(selected( by default)?\\)\\x20*$", "");
      pureTitle = DEFAULT_TITLE_FORMAT;
      selected = !path.equals(purePath);
    }

    return new ChoiceTokens(purePath, pureTitle, selected);
  }

  private List<CompositeInclusion> getIncludes(LogConfigEntry logConfigEntry) {
    return logConfigEntry.getIncludes()
        .stream()
        .map(inclusion -> new CompositeInclusion(inclusion.getNode(), convertPathToUnix(inclusion.getPath())))
        .collect(toList());
  }

  /**
   * A POJO for storing results of parsing of plain log path string (from 'plainLogs' config section)
   * @author Toparvion
   */
  static class ChoiceTokens {
    private final String purePath;
    private final String pureTitle;
    private final boolean selected;

    ChoiceTokens(String purePath, String pureTitle, boolean selected) {
      this.purePath = purePath;
      this.pureTitle = pureTitle;
      this.selected = selected;
    }

    String getPurePath() {
      return purePath;
    }

    String getPureTitle() {
      return pureTitle;
    }

    boolean isSelected() {
      return selected;
    }
  }
}
