package ru.ftc.upc.testing.analog.model;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A data describing any general failure on the server side to clients
 *
 * @author Toparvion
 * @since v0.7
 */
public class ServerFailure implements Serializable {
  private final String message;
  private final ZonedDateTime timestamp;

  public ServerFailure(String message, ZonedDateTime timestamp) {
    this.message = message;
    this.timestamp = timestamp;
  }

  public String getMessage() {
    return message;
  }

  public ZonedDateTime getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return "ServerFailure{" +
        "message='" + message + '\'' +
        ", timestamp=" + timestamp +
        '}';
  }
}
