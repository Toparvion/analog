package ru.ftc.upc.testing.analog.service;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * Date: 02.10.14
 * Time: 12:48
 */
public class ReadingMetaData implements Serializable {
  private long prependingCounter = -1L;
  private long appendingCounter = 0L;
  private long fileSavedSize = 0L;

  public long getPrependingCounter() {
    return prependingCounter;
  }

  public void setPrependingCounter(long prependingCounter) {
    this.prependingCounter = prependingCounter;
  }

  public long getAppendingCounter() {
    return appendingCounter;
  }

  public void setAppendingCounter(long appendingCounter) {
    this.appendingCounter = appendingCounter;
  }

  public long getFileSavedSize() {
    return fileSavedSize;
  }

  public void setFileSavedSize(long fileSavedSize) {
    this.fileSavedSize = fileSavedSize;
  }

  public boolean isPrependingCounterSet() {
    return (prependingCounter != -1L);
  }
}
