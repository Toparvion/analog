/*
package tech.toparvion.analog.remote.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.MessageSource;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext.IntegrationFlowRegistration;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport.FileTailingEvent;
import org.springframework.messaging.Message;
import tech.toparvion.analog.model.remote.AccessViolationTailingEvent;
import tech.toparvion.analog.remote.agent.origin.restrict.FileAccessGuard;
import tech.toparvion.analog.service.origin.LogEventTypeDetector;

import java.io.File;
import java.security.AccessControlException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static tech.toparvion.analog.model.LogEventType.LOG_NOT_FOUND;
import static tech.toparvion.analog.model.LogEventType.LOG_ROTATED;
import static tech.toparvion.analog.remote.agent.AgentConstants.*;
import static tech.toparvion.analog.remote.agent.AgentUtils.composeTrackingFlowId;
import static tech.toparvion.analog.util.PathUtils.convertToUnixStyle;

*/
/**
 * @author Toparvion
 * @since v0.14
 *//*

class TailEventHandlerTest {

  @Mock IntegrationFlowContext flowContextMock;
  @Mock LogEventTypeDetector logTypeEventDetectorMock;
  @Mock FileAccessGuard fileAccessGuardMock;
  @Mock MessageSource messageSourceMock;

  TailEventHandler sut;

  @BeforeEach
  void setUp() {
    initMocks(this);
    sut = new TailEventHandler(flowContextMock, logTypeEventDetectorMock,
        fileAccessGuardMock, messageSourceMock);
  }

  @Test
  @DisplayName("Tailing event is sent to the clients and contains the data for UI message")
  void normalExecution() {
    // given
    var logFileName = "app.log";
    var logFile = new File(logFileName);
    String logPath = convertToUnixStyle(logFile.getAbsolutePath());
    var tailingFlowId = TAIL_FLOW_PREFIX + logPath;
    var trackingFlowId = composeTrackingFlowId(FLAT_PREFIX, logPath);
    var eventSource = new Object();
    var messageText = "file not found";
    var event = new FileTailingEvent(eventSource, messageText, logFile);

    when(logTypeEventDetectorMock.detectEventType(event)).thenReturn(LOG_NOT_FOUND);

    when(flowContextMock.getRegistrationById(tailingFlowId))
        .thenReturn(mock(IntegrationFlowRegistration.class));
    var flatTrackingFlowRegMock = mock(IntegrationFlowRegistration.class);
    when(flowContextMock.getRegistrationById(trackingFlowId))
        .thenReturn(flatTrackingFlowRegMock);
    var trackingFlowMock = mock(StandardIntegrationFlow.class);
    when(flatTrackingFlowRegMock.getIntegrationFlow())
        .thenReturn(trackingFlowMock);
    var trackingFlowOutChannel = mock(PublishSubscribeChannel.class);
    when(trackingFlowMock.getIntegrationComponents())
        .thenReturn(Map.of(trackingFlowOutChannel, ""));

    // when
    sut.processFileTailingEvent(event);

    // then
    verify(logTypeEventDetectorMock).detectEventType(event);
    verifyNoInteractions(fileAccessGuardMock);
    verify(flowContextMock, never()).remove(tailingFlowId);

    verify(flowContextMock).getRegistrationById(trackingFlowId);
    verify(flatTrackingFlowRegMock).getIntegrationFlow();
    verify(trackingFlowMock).getIntegrationComponents();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(trackingFlowOutChannel).send(messageCaptor.capture());

    Message<?> message = messageCaptor.getValue();
    assertTrue(message.getPayload() instanceof FileTailingEvent);
    var fileTailingEvent = (FileTailingEvent) message.getPayload();
    assertTrue(fileTailingEvent.toString().contains(messageText));
    assertSame(logFile, fileTailingEvent.getFile());
    assertSame(eventSource, fileTailingEvent.getSource());
  }

  @Test
  @DisplayName("Access violation exception is sent to the clients")
  void processEventWithAccessViolation() {
    // given
    var logFileName = "app.log";
    var logFile = new File(logFileName);
    String logPath = convertToUnixStyle(logFile.getAbsolutePath());
    var tailingFlowId = TAIL_FLOW_PREFIX + logPath;
    var trackingFlowId = composeTrackingFlowId(FLAT_PREFIX, logPath);
    var eventSource = new Object();
    var messageText = "file not found";
    var event = new FileTailingEvent(eventSource, messageText, logFile);

    when(logTypeEventDetectorMock.detectEventType(event)).thenReturn(LOG_ROTATED);
    doThrow(AccessControlException.class).when(fileAccessGuardMock).checkAccess(logPath);
    when(flowContextMock.getRegistrationById(tailingFlowId))
        .thenReturn(mock(IntegrationFlowRegistration.class));
    var flatTrackingFlowRegMock = mock(IntegrationFlowRegistration.class);
    when(flowContextMock.getRegistrationById(trackingFlowId))
        .thenReturn(flatTrackingFlowRegMock);
    var trackingFlowMock = mock(StandardIntegrationFlow.class);
    when(flatTrackingFlowRegMock.getIntegrationFlow())
        .thenReturn(trackingFlowMock);
    var trackingFlowOutChannel = mock(PublishSubscribeChannel.class);
    when(trackingFlowMock.getIntegrationComponents())
        .thenReturn(Map.of(trackingFlowOutChannel, ""));

    // when
    sut.processFileTailingEvent(event);

    // then
    verify(logTypeEventDetectorMock).detectEventType(event);
    verify(fileAccessGuardMock).checkAccess(logPath);
    verify(flowContextMock).getRegistrationById(tailingFlowId);
    verify(flowContextMock).remove(tailingFlowId);

    verify(flowContextMock).getRegistrationById(trackingFlowId);
    verify(flatTrackingFlowRegMock).getIntegrationFlow();
    verify(trackingFlowMock).getIntegrationComponents();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(trackingFlowOutChannel).send(messageCaptor.capture());

    Message<?> message = messageCaptor.getValue();
    assertTrue(message.getPayload() instanceof AccessViolationTailingEvent);
    var accessViolationTailingEvent = (AccessViolationTailingEvent) message.getPayload();
    assertTrue(accessViolationTailingEvent.toString().contains(messageText));
    assertSame(logFile, accessViolationTailingEvent.getFile());
    assertSame(eventSource, accessViolationTailingEvent.getSource());
  }

  @Test
  @DisplayName("The exception is thrown when no tracking flow found")
  void noTrackingFlowFound() {
    // given
    var logFileName = "app.log";
    var logFile = new File(logFileName);
    String logPath = convertToUnixStyle(logFile.getAbsolutePath());
    var flatTrackingFlowId = composeTrackingFlowId(FLAT_PREFIX, logPath);
    var groupTrackingFlowId = composeTrackingFlowId(GROUP_PREFIX, logPath);
    var eventSource = new Object();
    var messageText = "file not found";
    var event = new FileTailingEvent(eventSource, messageText, logFile);

    when(logTypeEventDetectorMock.detectEventType(event)).thenReturn(LOG_ROTATED);

    // when
    Executable sutAction = () -> sut.processFileTailingEvent(event);

    // then
    var exception = assertThrows(IllegalStateException.class, sutAction);
    verify(logTypeEventDetectorMock).detectEventType(event);
    verify(fileAccessGuardMock).checkAccess(logPath);

    verify(flowContextMock).getRegistrationById(flatTrackingFlowId);
    verify(flowContextMock).getRegistrationById(groupTrackingFlowId);
    assertTrue(exception.getMessage().contains(event.toString()));
  }
}*/
