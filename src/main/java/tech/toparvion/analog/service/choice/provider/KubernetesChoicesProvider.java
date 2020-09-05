package tech.toparvion.analog.service.choice.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import tech.toparvion.analog.model.config.entry.LogPath;

import java.util.List;

/**
 * @author Toparvion
 */
@Component
@RequestScope
public class KubernetesChoicesProvider {
  private static final Logger log = LoggerFactory.getLogger(KubernetesChoicesProvider.class);

  public List<String> expandPath(LogPath includedLogPath) {
    return null;
  }
}
