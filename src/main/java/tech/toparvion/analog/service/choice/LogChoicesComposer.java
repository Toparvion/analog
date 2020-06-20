package tech.toparvion.analog.service.choice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.api.LogChoice;
import tech.toparvion.analog.model.api.LogChoiceBuilder;
import tech.toparvion.analog.model.config.ChoiceGroup;
import tech.toparvion.analog.model.config.ChoiceProperties;
import tech.toparvion.analog.model.config.entry.*;
import tech.toparvion.analog.service.choice.provider.FileSystemScanner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static tech.toparvion.analog.service.choice.LogChoiceUtils.expandTitle;
import static tech.toparvion.analog.util.AnaLogUtils.nvls;
import static tech.toparvion.analog.util.PathUtils.convertToUnixStyle;

/**
 * @author Toparvion
 * @since v0.7
 */
@Service
public class LogChoicesComposer {
  private static final Logger log = LoggerFactory.getLogger(LogChoicesComposer.class);

  private final List<ChoiceGroup> choices;
  private final FileSystemScanner fileSystemScanner;

  @Autowired
  public LogChoicesComposer(ChoiceProperties choiceProperties, FileSystemScanner fileSystemScanner) {
    this.choices = choiceProperties.getChoices();
    this.fileSystemScanner = fileSystemScanner;
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
    choices.addAll(processScanLocations(group));
    return choices.stream();
  }

  private Set<LogChoice> processCompositeLogs(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    // traverse and process all of the path entries as they are commonly used in groups
    for (CompositeLogConfigEntry compositeEntry : group.getCompositeLogs()) {
      String title = nvls(compositeEntry.getTitle(), compositeEntry.getHKey());
      LogChoice choice = new LogChoiceBuilder()
          .setGroup(groupName)
          .setId(compositeEntry.getId())
          .setType(compositeEntry.getType().toString())
          .setTitle(title)
          .setSelected(compositeEntry.isSelected())
          .setIncludes(getInclusions(compositeEntry))
          .createLogChoice();
      choices.add(choice);
    }
    return choices;
  }

  private Set<LogChoice> processPlainLogs(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();
    for (PlainLogConfigEntry plainEntry : group.getPlainLogs()) {
      // first involve graceful check for non-absolute paths
      if (plainEntry.getType() == LogType.LOCAL_FILE) {
        Path fullPath = Paths.get(plainEntry.getPath().getFullPath());
        if (!fullPath.isAbsolute()) {
          log.warn("Plain log path '{}' in group '{}' is not absolute and thus will be excluded from choices " +
              "on the client.", fullPath, groupName);
          continue;
        }
      }
      String title = nvls(plainEntry.getTitle(), "$f ($g)");
      String expandedTitle = expandTitle(title, plainEntry.getPath().getFullPath(), groupName);
      // here we're processing paths coming from config file so there is no need to clean them out from leading slash
      LogChoice choice = new LogChoiceBuilder()
          .setGroup(groupName)
          .setId(convertToUnixStyle(plainEntry.getId()))
          .setType(plainEntry.getType().toString())
          .setTitle(expandedTitle)
          .setSelected(plainEntry.isSelected())
          .setNode(plainEntry.getPath().getNode())
          .createLogChoice();
      choices.add(choice);
    }
    return choices;
  }

  private Set<LogChoice> processScanLocations(ChoiceGroup group) {
    ScanLocations scanLocations = group.getScanLocations();
    if (scanLocations == null) {
      return emptySet();
    }
    if (!scanLocations.getDocker().isEmpty()) {
      log.warn("Docker containers scanning is not supported yet. Please contact @toparvion to implement it. " +
          "Thank you for your interest!");
    }
    if (!scanLocations.getKubernetes().isEmpty()) {
      log.warn("Kubernetes resources scanning is not supported yet. Please contact @toparvion to implement it. " +
          "Thank you for your interest!");
    }
    return fileSystemScanner.scanForChoices(scanLocations.getDirectories(), group.getGroup());
  }

  private List<String> getInclusions(CompositeLogConfigEntry logConfigEntry) {
    return logConfigEntry.getIncludes()
        .stream()
        .map(CompositeInclusion::getPath)
        .map(inclusion -> convertToUnixStyle(inclusion.getFullPath()))
        .collect(toList());
  }
}
