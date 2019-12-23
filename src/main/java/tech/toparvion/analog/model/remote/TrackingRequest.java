package tech.toparvion.analog.model.remote;

import tech.toparvion.analog.model.config.entry.LogPath;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * A set of parameters needed to establish log tracking on remote agent. The need of the set originates from the fact
 * that configurations on various AnaLog nodes do not have to be the same and hence an agent cannot rely on its own
 * configuration in order to extract any parameters for establishing of remote tracking.
 * @author Toparvion
 * @since v0.7
 */
public class TrackingRequest implements Serializable {
  private final LogPath logPath;
  /** May be null in case of plain (non-composite) log */
  @Nullable
  private final String timestampFormat;
  /**
   * Logical address for broadcasting log's records to WebSocket clients. Usually looks exactly as specified on the
   * client side, e.g. {@code node://angara/home/upc/app.log}.<p>
   * May be null in case switching the tracking off.
   */
  @Nullable
  private final String clientDestination;
  private final boolean isTailNeeded;

  public TrackingRequest(LogPath logPath,
                         @Nullable String timestampFormat,
                         @Nullable String clientDestination,
                         boolean isTailNeeded) {
    this.logPath = logPath;
    this.timestampFormat = timestampFormat;
    this.clientDestination = clientDestination;
    this.isTailNeeded = isTailNeeded;
  }

  public LogPath getLogPath() {
    return logPath;
  }

  @Nullable
  public String getTimestampFormat() {
    return timestampFormat;
  }

  @Nullable
  public String getClientDestination() {
    return clientDestination;
  }

  public boolean isTailNeeded() {
    return isTailNeeded;
  }

  /**
   * A request with no timestamp format is considered 'flat' as it cannot be involved into lines grouping logic and
   * thus is suitable for flat tracking only.
   * @return {@code true} if this is a request for flat tracking only
   */
  public boolean isFlat() {
    return (timestampFormat == null);
  }

  @Override
  public String toString() {
    return "TrackingRequest{" +
        "logPath='" + logPath + '\'' +
        ", timestampFormat='" + timestampFormat + '\'' +
        ", isFlat='" + isFlat() + '\'' +
        ", clientDestination='" + clientDestination + '\'' +
        ", isTailNeeded=" + isTailNeeded +
        '}';
  }
}
