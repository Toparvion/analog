package tech.toparvion.analog.model;

import java.io.Serializable;

/**
 * A piece of data that server can send to clients during watching session. Mostly contains tail specific information
 * like event type (log creation, rotation, etc), path to log file and log's node name.
 *
 * @author Toparvion
 * @since v0.7
 */
@SuppressWarnings("unused")   // getters are actually used by Jackson serializer during conversion to JSON
public class Metadata implements Serializable {
  private final LogEventType eventType;
  private final String logPath;
  private final String nodeName;
  private final String message;

  public Metadata(LogEventType eventType, String logPath, String nodeName, String message) {
    this.eventType = eventType;
    this.logPath = logPath;
    this.nodeName = nodeName;
    this.message = message;
  }

  public String getEventType() {
    return eventType.toString();
  }

  public String getLogPath() {
    return logPath;
  }

  public String getNodeName() {
    return nodeName;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "Metadata{" +
            "eventType=" + eventType +
            ", logPath='" + logPath + '\'' +
            ", nodeName='" + nodeName + '\'' +
            ", message='" + message + '\'' +
            '}';
  }
}
