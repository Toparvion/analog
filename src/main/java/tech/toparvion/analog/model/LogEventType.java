package tech.toparvion.analog.model;

/**
 * Types of events that may happen with a log being followed by tracking process and that AnaLog is aware of.
 * All the types are mapped to their ID on client side (so called {@code jsValue}'s). Thus the server events can be
 * easily treated as client events in a similar fashion.
 *
 * @author Toparvion
 * @since v0.7
 * @see "static/notification/notification.constant.js"
 */
public enum LogEventType {
  LOG_NOT_FOUND("logNotFound"),
  LOG_APPEARED("logAppeared"),
  LOG_ROTATED("logRotated"),
  LOG_DISAPPEARED("logDisappeared"),
  LOG_TRUNCATED("logTruncated"),
  UNRECOGNIZED("unrecognized");

  private final String jsValue;

  LogEventType(String jsValue) {
    this.jsValue = jsValue;
  }

  @Override
  public String toString() {
    return jsValue;
  }
}
