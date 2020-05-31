package tech.toparvion.analog.service.choice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import tech.toparvion.analog.model.config.ChoicesAutoReloadProperties;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.Executor;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Configuration for choices list auto reloading
 *
 * @author Polyudov
 * @since v0.14
 */
@Configuration
@ConditionalOnChoicesAutoReloadEnabled
class ChoicesAutoReloadConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ChoicesAutoReloadConfiguration.class);
  static final String CHOICES_HOT_RELOAD_EXECUTOR = "choicesAutoReloadExecutor";

  @Bean(CHOICES_HOT_RELOAD_EXECUTOR)
  Executor choicesHotReloadExecutor() {
    return newSingleThreadExecutor(new CustomizableThreadFactory("hot-reload"));
  }

  @Bean
  @Nullable
  FileWatcherProvider fileWatcherProvider(ChoicesAutoReloadProperties choiceProperties) {
    String choicesPropertiesLocation = choiceProperties.getLocation();
    if (isNullOrEmpty(choicesPropertiesLocation)) {
      log.info("Custom path for choices list is not present in 'choices.auto-reload.path' property. Hot reload logic won't be applied.");
      return null;
    }

    try {
      Path choicesPropertiesPath = Paths.get(choicesPropertiesLocation); //check exception

      if (!exists(choicesPropertiesPath)) {
        log.warn("File '{}' does not exist", choicesPropertiesLocation);
        return null;
      }
      if (isDirectory(choicesPropertiesPath)) {
        log.warn("'choices.auto-reload.path' ('{}') is a directory. Please specify a path to a regular file to enable choices hot reloading.", choicesPropertiesLocation);
        return null;
      }
      return new FileWatcherProvider(choicesPropertiesPath);
    } catch (Exception e) {
      log.warn("Can`t create choices list watcher", e);
      return null;
    }
  }

  static class FileWatcherProvider {
    private final Path watchDir;
    private final Path choicesPropertiesPath;
    private final WatchService watchService;

    FileWatcherProvider(Path choicesPropertiesPath) throws IOException {
      this.choicesPropertiesPath = choicesPropertiesPath;
      this.watchDir = getParent(choicesPropertiesPath);
      this.watchService = FileSystems.getDefault().newWatchService();

      watchDir.register(watchService, ENTRY_MODIFY);
    }

    public boolean isChoicesPropertiesFileEvent(Path eventPath) {
      Path eventResolvedPath = watchDir.resolve(eventPath);
      return Objects.equals(choicesPropertiesPath, eventResolvedPath);
    }

    public WatchService getWatchService() {
      return watchService;
    }

    private Path getParent(Path path) {
      return requireNonNullElse(path.getParent(), path);
    }
  }
}
