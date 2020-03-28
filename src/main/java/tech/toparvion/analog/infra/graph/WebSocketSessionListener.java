package tech.toparvion.analog.infra.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import tech.toparvion.analog.infra.graph.model.Client;
import tech.toparvion.analog.infra.graph.repo.ClientRepository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.time.Clock;

/**
 * @author Toparvion
 * @since v0.14
 */
public class WebSocketSessionListener extends WebSocketHandlerDecorator {
  private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

  private final ClientRepository clientRepository;

  public WebSocketSessionListener(WebSocketHandler delegate, ClientRepository clientRepository) {
    super(delegate);
    this.clientRepository = clientRepository;
  }

  @Override
  public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws Exception {
    super.afterConnectionEstablished(session);
    String ip = extractIp(session);
    if (ip == null) {
      return;
    }
    log.info("Detected new websocket session ID={} initiated from IP={}.", session.getId(), ip);
    Client savedClient = clientRepository.save(new Client(ip, Clock.systemDefaultZone().instant()));
    log.info("Saved client node with id={}", savedClient.getId());
  }

  @Override
  public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus closeStatus) throws Exception {
    super.afterConnectionClosed(session, closeStatus);
    String ip = extractIp(session);
    Long id = clientRepository.deleteClientByIp(ip);
    log.info("Removed client id={} by IP={}", id, ip);
  }

  @Nullable
  private String extractIp(WebSocketSession session) {
    InetSocketAddress remoteAddress = session.getRemoteAddress();
    if (remoteAddress == null) {
      log.warn("Unable to obtain client's remote address from WebSocket session: {}", session);
      return null;
    }
    return remoteAddress.getAddress().getHostAddress();
  } 
}
