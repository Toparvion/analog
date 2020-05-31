package tech.toparvion.analog.service.choice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.endpoint.RefreshEndpoint;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.service.choice.ChoicesAutoReloadConfiguration.FileWatcherProvider;

import javax.annotation.Nullable;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static tech.toparvion.analog.service.choice.ChoicesAutoReloadConfiguration.CHOICES_HOT_RELOAD_EXECUTOR;

/**
 * @author Polyudov
 * @since v0.14
 */
@Service
@EnableAsync
@ConditionalOnChoicesAutoReloadEnabled
class ChoicesPropertiesChangesListener {
  private static final Logger log = LoggerFactory.getLogger(ChoicesPropertiesChangesListener.class);

  private final RefreshEndpoint refreshEndpoint;
  private final FileWatcherProvider fileWatcherProvider;

  @Autowired
  ChoicesPropertiesChangesListener(RefreshEndpoint refreshEndpoint,
                                   @Nullable FileWatcherProvider fileWatcherProvider) {
    this.refreshEndpoint = refreshEndpoint;
    this.fileWatcherProvider = fileWatcherProvider;
  }

  @Async(CHOICES_HOT_RELOAD_EXECUTOR)
  @EventListener(ApplicationReadyEvent.class)
  public void watchFile() {
    if (fileWatcherProvider == null) {
      return;
    }
    try {
      log.info("Start watching for the choices properties file: '{}'", fileWatcherProvider.getChoicesPropertiesPath().toAbsolutePath());
      WatchService watchService = fileWatcherProvider.getWatchService();
      WatchKey key;
      while ((key = watchService.take()) != null) {
        for (WatchEvent<?> event : key.pollEvents()) {
          //Filter some special events and repeated events
          if (OVERFLOW.equals(event.kind()) || event.count() > 1) {
            continue;
          }
          Path path = (Path) event.context();
          //Check modified file
          if (fileWatcherProvider.isChoicesPropertiesFileEvent(path)) {
            refreshEndpoint.refresh();
            log.info("Choices properties file was reloaded");
          }
        }
        boolean reset = key.reset();
        if (!reset) {
          log.error("Choices properties changes watching is now invalid");
          throw new ClosedWatchServiceException();
        }
      }
    } catch (ClosedWatchServiceException ignored) { //this exception throws during a normal application shutdown
    } catch (Exception e) {
      log.error("Stop watching for the choices properties file because of error:", e);
    }
  }
}
