package ru.ftc.upc.testing.analog.remote.agent;

import org.springframework.messaging.Message;

import java.time.LocalDateTime;

import static ru.ftc.upc.testing.analog.remote.RemotingConstants.LOG_TIMESTAMP_VALUE__HEADER;

/**
 * Simple yet stateful correlationId provider that relies on messages timestamps extracted by
 * {@link ru.ftc.upc.testing.analog.util.timestamp.TimestampExtractor}.
 * It should be noticed that the enricher relies on the fact that lines are delivered into the flow in a roughly
 * ascending timestamp order. While this is obvious for log tailing, it must be provided explicitly in case of manual
 * log navigation.<p>
 * This enricher is intended for use for single log file only and thus <em>is not thread safe</em>.
 *
 * @author Toparvion
 * @since v0.7
 */
class CorrelationIdHeaderEnricher {
  /**
   * Current (acting) correlationId value.
   * Defaults to current system time at the moment of class instance creation.
   */
  private Long currentCorrelationId = System.nanoTime();

  Long obtainCorrelationId(Message<String> lineMessage) {
    LocalDateTime lineTimestamp = lineMessage.getHeaders().get(LOG_TIMESTAMP_VALUE__HEADER, LocalDateTime.class);
    if (lineTimestamp == null) {
      return currentCorrelationId;
    }

    /* Sometimes sequential records of a log have exactly the same timestamps. This is likely to happen when the
     timestamp precision is not very high while log is getting filled quite actively. To address this problem we don't
     use records' timestamps as correlationId. Instead we use separate value that is new for every invocation.
     Obviously the best candidate for such a value is current system time (taken in nanos to ensure changing between
     frequent invocations).*/
    currentCorrelationId = System.nanoTime();
    return currentCorrelationId;
  }

}
