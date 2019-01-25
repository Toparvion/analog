package tech.toparvion.analog.remote.agent.origin.adapt;

import com.github.zafarkhaja.semver.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.config.adapters.AdaptersProperties;
import tech.toparvion.analog.model.config.adapters.ContainerAdapterParams;
import tech.toparvion.analog.model.config.adapters.GeneralAdapterParams;

/**
 * @author Toparvion
 * @since 0.11
 */
@Lazy
@Service
public class DockerOriginAdapter extends AbstractOriginAdapter {

  private static final Version TESTED_VERSION = Version.valueOf("18.6.0-ce");

  private final ContainerAdapterParams dockerParams;

  @Autowired
  public DockerOriginAdapter(AdaptersProperties adaptersProperties) {
    dockerParams = adaptersProperties.getDocker();
  }

  @Override
  public GeneralAdapterParams adapterParams() {
    return dockerParams;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    String detectionCommand = String.format("%s %s", dockerParams.getExecutable(), dockerParams.getVersionCommand());
    String idfString = obtainIdfString(detectionCommand);
    Assert.hasText(idfString, "docker client returned empty version string");
    log.debug("Raw docker client version: {}", idfString);
    idfString = idfString.replaceAll("\\b0+([\\d&&[^0]])", "$1");   // turn 18.06.10 into 18.6.10
    idfString = idfString.replaceAll("^'(.*)'$", "$1");             // '18.6.10' into 18.6.10
    Version dockerClientVersion = Version.valueOf(idfString);

    if (dockerClientVersion.getMajorVersion() > TESTED_VERSION.getMajorVersion()) {
      log.warn("Current Docker client version {} is greater than its tested version {}.",
              dockerClientVersion.getMajorVersion(), TESTED_VERSION.getMajorVersion());
    }
    log.info("Initialized Docker adapter upon client v{}.", dockerClientVersion);
  }
}
