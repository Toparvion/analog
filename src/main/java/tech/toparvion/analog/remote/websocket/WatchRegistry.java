package tech.toparvion.analog.remote.websocket;

import org.springframework.stereotype.Component;
import tech.toparvion.analog.model.config.entry.AbstractLogConfigEntry;

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
   * Key: AbstractLogConfigEntry (either from the config or created on-the-fly) which is being watched by sessions in value
   * Value: a list of websocket session IDs that are currently watching the log in key
   */
  private final Map<AbstractLogConfigEntry, List<String>> storage = new ConcurrentHashMap<>();

  void addEntry(AbstractLogConfigEntry logConfig, String sessionId) {
    List<String> sids = new LinkedList<>();
    sids.add(sessionId);
    storage.put(logConfig, sids);
  }

  @Nullable
  List<String> findWatchingSessionsFor(AbstractLogConfigEntry logConfigEntry) {
    return storage.get(logConfigEntry);
  }

  AbstractLogConfigEntry findLogConfigEntryBy(String sessionId) {
    return storage.entrySet().stream()
        .filter(mapEntry -> mapEntry.getValue().contains(sessionId))
        .findAny()
        .map(Map.Entry::getKey)
        .orElseThrow(() -> new IllegalStateException("No watching log config entry found for sessionId=" + sessionId));
  }

  /**
   * Returns a list of sessions' ids that watch the same log.
   * @implNote It is generally a bad idea to return {@code null} from
   * methods with collection return type as absence of result can be expressed with an emptiness of returned
   * collection. But in this case the collection emptiness indicates that all the sessions for a log finished but the
   * log itself can still be used further.
   *
   * @param sessionId the session to find fellows for
   * @return a list of sessions watching the same log or {@code null} if there is no such sessions
   */
  @Nullable
  List<String> findFellowsFor(String sessionId) {
    return storage.values().stream()
        .filter(ids -> ids.contains(sessionId))
        .findAny()
        .orElse(null);
  }

  void removeEntry(AbstractLogConfigEntry logConfig) {
    storage.remove(logConfig);
  }
}
