package tech.toparvion.analog.model;

/**
 * Types of events that may happen with a file being followed by tail add that AnaLog is aware of.
 * All the types are mapped to their ID on client side (so called {@code jsValue}'s). Thus the server events can be
 * easily treated as client events in a similar fashion.
 *
 * @author Toparvion
 * @since v0.7
 */
public enum TailEventType {
  FILE_NOT_FOUND("fileNotFound"),
  FILE_APPEARED("fileAppeared"),
  FILE_ROTATED("fileRotated"),
  FILE_DISAPPEARED("fileDisappeared"),
  FILE_TRUNCATED("fileTruncated");

  private final String jsValue;

  TailEventType(String jsValue) {
    this.jsValue = jsValue;
  }

  @Override
  public String toString() {
    return jsValue;
  }
}
