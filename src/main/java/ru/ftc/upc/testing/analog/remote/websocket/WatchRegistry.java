package ru.ftc.upc.testing.analog.remote.websocket;

import org.springframework.stereotype.Component;
import ru.ftc.upc.testing.analog.model.config.LogConfigEntry;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A storage of bindings between logs being watched and corresponding lists of websocket session ids.
 *
 * @author Toparvion
 * @since v0.7
 */
@Component
public class WatchRegistry {
  /**
   * Key: LogConfigEntry (either from the config or created on-the-fly) which is being watched by sessions in value
   * Value: a list of websocket session IDs that are currently watching the log in key
   */
  private final Map<LogConfigEntry, List<String>> storage = new ConcurrentHashMap<>();

  public void addEntry(LogConfigEntry logConfig, String sessionId) {
    List<String> sids = new LinkedList<>();
    sids.add(sessionId);
    storage.put(logConfig, sids);
  }

  @Nullable
  public List<String> findWatchingSessionsFor(LogConfigEntry logConfigEntry) {
    return storage.get(logConfigEntry);
  }

  public LogConfigEntry findLogConfigEntryBy(String sessionId) {
    return storage.entrySet().stream()
        .filter(mapEntry -> mapEntry.getValue().contains(sessionId))
        .findAny()
        .map(Map.Entry::getKey)
        .orElseThrow(() -> new IllegalStateException("No watching log config entry found for sessionId=" + sessionId));
  }

  @Nullable
  public List<String> findFellowsFor(String sessionId) {
    return storage.values().stream()
        .filter(ids -> ids.contains(sessionId))
        .findAny()
        .orElse(null);
  }

  public void removeEntry(LogConfigEntry logConfig) {
    storage.remove(logConfig);
  }
}
