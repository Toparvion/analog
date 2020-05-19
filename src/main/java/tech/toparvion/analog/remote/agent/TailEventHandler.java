package tech.toparvion.analog.remote.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tech.toparvion.analog.model.LogEventType;
import tech.toparvion.analog.model.remote.AccessViolationTailingEvent;
import tech.toparvion.analog.model.remote.TrackingRequest;
import tech.toparvion.analog.remote.agent.origin.restrict.FileAccessGuard;
import tech.toparvion.analog.service.origin.LogEventTypeDetector;
import tech.toparvion.analog.util.AnaLogUtils;
import tech.toparvion.analog.util.LocalizedLogger;

import java.net.InetSocketAddress;
import java.security.AccessControlException;
import java.util.List;

import static tech.toparvion.analog.model.LogEventType.*;
import static tech.toparvion.analog.remote.agent.AgentConstants.*;
import static tech.toparvion.analog.remote.agent.AgentUtils.composeTrackingFlowId;
import static tech.toparvion.analog.remote.agent.AgentUtils.extractOutChannel;
import static tech.toparvion.analog.util.AnaLogUtils.doSafely;
import static tech.toparvion.analog.util.PathUtils.convertToUnixStyle;

/**
 * @author Toparvion
 * @since v0.12
 */
@Component
public class TailEventHandler {
  /**
   * List of event types that in case of happening must be additionally checked against access restriction
   */
  private static final List<LogEventType> SPECIAL_EVENT_TYPES = List.of(LOG_APPEARED, LOG_TRUNCATED, LOG_ROTATED);

  private final IntegrationFlowContext flowContext;
  private final LogEventTypeDetector dispatcher;
  private final FileAccessGuard fileAccessGuard;

  private final LocalizedLogger log;

  @Autowired
  public TailEventHandler(IntegrationFlowContext flowContext,
                          LogEventTypeDetector dispatcher,
                          FileAccessGuard fileAccessGuard,
                          MessageSource messageSource) {
    this.flowContext = flowContext;
    this.dispatcher = dispatcher;
    this.fileAccessGuard = fileAccessGuard;
    log = new LocalizedLogger(this, messageSource);
  }
  
  @EventListener
  public void processFileTailingEvent(FileTailingEvent tailingEvent) {
    log.debug("received-tailing-event", tailingEvent.toString());
    String logPath = convertToUnixStyle(tailingEvent.getFile().getPath());

    // in case of log appearing we must additionally check its path against access restrictions as the log may be 
    // a symlink to some real file that in turn is located in denied location
    LogEventType eventType = dispatcher.detectEventType(tailingEvent);
    if (SPECIAL_EVENT_TYPES.contains(eventType)) {
      // the following call will throw AccessControlException in case of violation
      try {
        fileAccessGuard.checkAccess(logPath);

      } catch (AccessControlException e) {
        log.warn("detected-access-violation", logPath);
        interruptTailFlow(logPath);
        // replace original event with a new one indicating the violation
        String message = AnaLogUtils.extractMessage(tailingEvent.toString());
        tailingEvent = new AccessViolationTailingEvent(tailingEvent.getSource(), message, tailingEvent.getFile());
      }
    }
    
    // and now send the event to subscribers of both flat and group tracking flow (if any) 
    boolean eventHandled = false;
    for (String prefix : new String[]{FLAT_PREFIX, GROUP_PREFIX}) {
      String trackingFlowId = composeTrackingFlowId(prefix, logPath);
      log.trace("trying-to-find-tracking-flow", trackingFlowId);
      IntegrationFlowContext.IntegrationFlowRegistration trackingRegistration = flowContext.getRegistrationById(trackingFlowId);
      if (trackingRegistration == null) {
        log.trace("not-found-tracking-registration-by-id", trackingFlowId);
        continue;
      }
      log.debug("found-tracking-registration", trackingFlowId);
      StandardIntegrationFlow trackingFlow = (StandardIntegrationFlow) trackingRegistration.getIntegrationFlow();
      PublishSubscribeChannel trackingOutChannel = extractOutChannel(trackingFlow);
      trackingOutChannel.send(MessageBuilder.withPayload(tailingEvent).build());
      log.debug("sent-tailing-event", trackingOutChannel.getComponentName(), tailingEvent);
      eventHandled = true;      // don't break the loop to let other flows to be notified as well
    }
    if (!eventHandled) {
      throw new IllegalStateException("No tracking found to accept tailing event: " + tailingEvent.toString());
    }
  }

  /**
   * Forcibly removes tailing flow for given {@code logPath}. Unlike
   * {@linkplain TrackingService#unregisterWatcher(TrackingRequest, InetSocketAddress) graceful stopping}, this method
   * does not check the count of subscribers for given tailing flow before removing. As a result, subsequent 
   * unregistering may end up with a warning about absence of underlying tailing flow. In case of access restriction 
   * violation this is considered normal.   
   * @param logPath full path to log which is followed by tailing flow
   */
  private void interruptTailFlow(String logPath) {
    String tailFlowId = TAIL_FLOW_PREFIX + logPath;
    IntegrationFlowContext.IntegrationFlowRegistration tailFlowRegistration = flowContext.getRegistrationById(tailFlowId);
    if (tailFlowRegistration == null) {
      log.error("not-found-tail-registration", tailFlowId);
      return;
    }
    doSafely(getClass(), () -> flowContext.remove(tailFlowId));
    log.debug("stopped-tailing-forcibly", tailFlowId);
  }

}
