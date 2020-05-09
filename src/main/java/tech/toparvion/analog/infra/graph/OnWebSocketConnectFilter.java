package tech.toparvion.analog.infra.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.socket.messaging.SubProtocolHandler;

import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.servlet.http.HttpServletResponse.SC_SWITCHING_PROTOCOLS;

/**
 * A servlet filter aimed to detect each new WebSocket session establishment and report it to the graph.<br/>
 * 
 * @implNote The implementation couldn't be an AOP aspect because {@linkplain SubProtocolHandler corresponding class}
 * doesn't allow to proxy itself neither with CGLIB nor with JDK proxy mechanism.
 * 
 * @author Toparvion
 * @since v0.14
 */
@Component
public class OnWebSocketConnectFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(OnWebSocketConnectFilter.class);
  private static final Pattern WS_SESSION_ID_PATTERN = Pattern.compile("/(\\w+)/websocket$");
  
  @Override
  protected void doFilterInternal(@Nonnull HttpServletRequest request, 
                                  @Nonnull HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    filterChain.doFilter(request, response);
    if (response.getStatus() != SC_SWITCHING_PROTOCOLS) {   // here we're interested in protocol upgrading only
      return;
    }
    String requestUri = request.getRequestURI();
    Matcher sessionIdMatcher = WS_SESSION_ID_PATTERN.matcher(requestUri);
    if (!sessionIdMatcher.find()) {
      log.warn("Couldn't extract websocket session ID from request URI: {}. Skipping graph updating.", requestUri);
      return;
    }
    String sid = sessionIdMatcher.group(1);
    log.info("Detected new websocket session ID={} initiated from IP={}.", sid, request.getRemoteAddr());
  }
}
