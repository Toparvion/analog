package ru.ftc.upc.testing.analog.model;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * A set of parameters needed to establish log tracking on remote agent. The need of the set originates from the fact
 * that configurations on various AnaLog nodes do not have to be the same and hence an agent cannot rely on its
 * configuration in order to extract any parameters to establish remote tracking.
 * @author Toparvion
 * @since v0.7
 */
public class TrackingRequest implements Serializable {
  private final String logFullPath;
  @Nullable
  private final String timestampFormat;
  private final String nodeName;
  private final String uid;

  public TrackingRequest(String logFullPath, @Nullable String timestampFormat, String nodeName, String uid) {
    this.logFullPath = logFullPath;
    this.timestampFormat = timestampFormat;
    this.nodeName = nodeName;
    this.uid = uid;
  }

  public String getLogFullPath() {
    return logFullPath;
  }

  @Nullable
  public String getTimestampFormat() {
    return timestampFormat;
  }

  @SuppressWarnings("unused")     // the method is called by means of Reflection API (through SpEL)
  public String getNodeName() {
    return nodeName;
  }

  public String getUid() {
    return uid;
  }

  /**
   * A request with no timestamp format is considered 'plain' as it cannot be involved into complex aggregating
   * tracking logic and thus is suitable for plain old tracking only.
   * @return {@code true} if this is a request for plain tracking only
   */
  public boolean isPlain() {
    return (timestampFormat == null);
  }

  @Override
  public String toString() {
    return "TrackingRequest{" +
        "logFullPath='" + logFullPath + '\'' +
        ", timestampFormat='" + timestampFormat + '\'' +
        ", nodeName='" + nodeName + '\'' +
        ", uid='" + uid + '\'' +
        '}';
  }

}
