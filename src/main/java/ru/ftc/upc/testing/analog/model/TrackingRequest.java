package ru.ftc.upc.testing.analog.model;

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
  private final String timestampFormat;
  private final String nodeName;
  private final String uid;

  public TrackingRequest(String logFullPath, String timestampFormat, String nodeName, String uid) {
    this.logFullPath = logFullPath;
    this.timestampFormat = timestampFormat;
    this.nodeName = nodeName;
    this.uid = uid;
  }

  public String getLogFullPath() {
    return logFullPath;
  }

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
