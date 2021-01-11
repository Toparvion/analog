package tech.toparvion.analog.service.choice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cloud.endpoint.RefreshEndpoint;
import tech.toparvion.analog.service.choice.ChoicesAutoReloadConfiguration.FileWatcherProvider;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Polyudov
 * @since v0.14
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
class ChoicesPropertiesChangesListenerTest {
  @Mock
  private Path path;
  @Mock
  private WatchEvent<?> watchEvent;
  @Mock
  private WatchService watchService;
  @Mock
  private WatchKey watchKey;
  @Mock
  private RefreshEndpoint refreshEndpoint;
  @Mock
  private FileWatcherProvider fileWatcherProvider;
  @InjectMocks
  private ChoicesPropertiesChangesListener sut;

  @BeforeEach
  void setUp() throws InterruptedException {
    initMocks(this);

    when(fileWatcherProvider.getChoicesPropertiesPath()).thenReturn(Path.of("\\"));
    when(fileWatcherProvider.getWatchService()).thenReturn(watchService);
    when(fileWatcherProvider.isChoicesPropertiesFileEvent(any())).thenReturn(true);
    when(watchService.take()).thenReturn(watchKey).thenReturn(null);
    when(watchKey.pollEvents()).thenReturn(List.of(watchEvent));
    when(watchKey.reset()).thenReturn(false);
    when(watchEvent.count()).thenReturn(1);
    doReturn(path)
        .when(watchEvent)
        .context();
    doReturn(ENTRY_MODIFY)
        .when(watchEvent)
        .kind();
  }

  @Test
  @DisplayName("Nothing happens if file watcher is null")
  void fileWatcherProviderIsNull() {
    sut = new ChoicesPropertiesChangesListener(refreshEndpoint, null);

    sut.watchFile();

    verifyNoInteractions(
        refreshEndpoint,
        fileWatcherProvider,
        watchService,
        watchEvent,
        watchKey);
  }

  @Test
  @DisplayName("Nothing happens if watchKey is null")
  void watchKeyIsNull() throws InterruptedException {
    when(watchService.take()).thenReturn(null);

    sut.watchFile();

    verify(fileWatcherProvider).getWatchService();
    verify(fileWatcherProvider).getChoicesPropertiesPath();
    verify(watchService).take();
    verifyNoMoreInteractions(watchService, fileWatcherProvider);
    verifyNoInteractions(refreshEndpoint, watchKey, watchEvent);
  }

  @Test
  @DisplayName("Nothing happens if watchEvent is equal to OVERFLOW")
  void overflowEventKind() throws InterruptedException {
    doReturn(OVERFLOW)
        .when(watchEvent)
        .kind();

    sut.watchFile();

    verify(fileWatcherProvider).getWatchService();
    verify(fileWatcherProvider).getChoicesPropertiesPath();
    verify(watchKey).pollEvents();
    verify(watchKey).reset();
    verify(watchService).take();
    verify(watchEvent).kind();
    verifyNoMoreInteractions(watchService, fileWatcherProvider, watchEvent, watchKey);
    verifyNoInteractions(refreshEndpoint);
  }

  @Test
  @DisplayName("Nothing happens if event is repeated")
  void eventCountMoreThenOne() throws InterruptedException {
    when(watchEvent.count()).thenReturn(2);

    sut.watchFile();

    verify(fileWatcherProvider).getWatchService();
    verify(fileWatcherProvider).getChoicesPropertiesPath();
    verify(watchKey).pollEvents();
    verify(watchKey).reset();
    verify(watchService).take();
    verify(watchEvent).kind();
    verify(watchEvent).count();
    verifyNoMoreInteractions(watchService, fileWatcherProvider, watchEvent, watchKey);
    verifyNoInteractions(refreshEndpoint);
  }

  @Test
  @DisplayName("Nothing happens if event is not assigned with watching file")
  void isNotChoicesPropertiesFileEvent() throws InterruptedException {
    when(fileWatcherProvider.isChoicesPropertiesFileEvent(any())).thenReturn(false);

    sut.watchFile();

    verify(fileWatcherProvider).getWatchService();
    verify(fileWatcherProvider).getChoicesPropertiesPath();
    verify(fileWatcherProvider).isChoicesPropertiesFileEvent(path);
    verify(watchKey).pollEvents();
    verify(watchKey).reset();
    verify(watchService).take();
    verify(watchEvent).kind();
    verify(watchEvent).count();
    verify(watchEvent).context();
    verifyNoMoreInteractions(watchService, fileWatcherProvider, watchEvent, watchKey);
    verifyNoInteractions(refreshEndpoint);
  }

  @Test
  @DisplayName("Refresh was called if event is correct")
  void callRefresh() throws InterruptedException {
    sut.watchFile();

    verify(fileWatcherProvider).getWatchService();
    verify(fileWatcherProvider).getChoicesPropertiesPath();
    verify(fileWatcherProvider).isChoicesPropertiesFileEvent(path);
    verify(watchKey).pollEvents();
    verify(watchKey).reset();
    verify(watchService).take();
    verify(watchEvent).kind();
    verify(watchEvent).count();
    verify(watchEvent).context();
    verify(refreshEndpoint).refresh();
    verifyNoMoreInteractions(
        watchService,
        fileWatcherProvider,
        watchEvent,
        watchKey,
        refreshEndpoint);
  }
}