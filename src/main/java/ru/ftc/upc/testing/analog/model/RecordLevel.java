package ru.ftc.upc.testing.analog.model;

/**
 * Log record levels which are supported by AnaLog's detection logic.
 * <p>Created by Toparvion on 25.02.2017.
 */
public enum RecordLevel {
  TRACE,
  DEBUG,
  INFO,
  WARN,
  ERROR,
  FATAL,
  UNKNOWN     // special level that applied if no other detected
}
