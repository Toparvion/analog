package tech.toparvion.analog.model;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * Date: 02.10.14
 * Time: 12:48
 */
public class ReadingMetaData implements Serializable {
  private long prependingCounter;
  private long appendingCounter;
  private long fileSavedSize;

  public ReadingMetaData() {
    reset();
  }

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

  public void reset() {
    prependingCounter = -1L;
    appendingCounter = 0L;
    fileSavedSize = 0L;
  }
}
