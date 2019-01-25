package tech.toparvion.analog.model.config.adapters;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author Toparvion
 * @since 0.11
 */
@Component
@ConfigurationProperties("tracking")
public class TrackingProperties {
  private Grouping grouping;
  private TailSize tailSize;
  private Duration retryDelay;

  public Grouping getGrouping() {
    return grouping;
  }

  public void setGrouping(Grouping grouping) {
    this.grouping = grouping;
  }

  public TailSize getTailSize() {
    return tailSize;
  }

  public void setTailSize(TailSize tailSize) {
    this.tailSize = tailSize;
  }

  public Duration getRetryDelay() {
    return retryDelay;
  }

  public void setRetryDelay(Duration retryDelay) {
    this.retryDelay = retryDelay;
  }

  @Override
  public String toString() {
    return "TrackingProperties{" +
            "grouping=" + grouping +
            ", tailSize=" + tailSize +
            ", retryDelay=" + retryDelay +
            '}';
  }

  public static class TailSize {
    private int flat;
    private int group;

    public int getFlat() {
      return flat;
    }

    public void setFlat(int flat) {
      this.flat = flat;
    }

    public int getGroup() {
      return group;
    }

    public void setGroup(int group) {
      this.group = group;
    }

    @Override
    public String toString() {
      return "TailSize{" +
              "flat=" + flat +
              ", group=" + group +
              '}';
    }
  }

  public static class Grouping {
    private int sizeThreshold;
    private Duration timeout;

    public int getSizeThreshold() {
      return sizeThreshold;
    }

    public void setSizeThreshold(int sizeThreshold) {
      this.sizeThreshold = sizeThreshold;
    }

    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(Duration timeout) {
      this.timeout = timeout;
    }

    @Override
    public String toString() {
      return "Grouping{" +
              "sizeThreshold=" + sizeThreshold +
              ", timeout=" + timeout +
              '}';
    }
  }
}
