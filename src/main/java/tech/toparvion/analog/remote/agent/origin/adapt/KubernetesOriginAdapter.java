package tech.toparvion.analog.remote.agent.origin.adapt;

import com.github.zafarkhaja.semver.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.config.adapters.AdaptersProperties;
import tech.toparvion.analog.model.config.adapters.ContainerAdapterParams;
import tech.toparvion.analog.model.config.adapters.GeneralAdapterParams;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Toparvion
 * @since 0.11
 */
@Lazy
@Service
public class KubernetesOriginAdapter extends AbstractOriginAdapter {

  private static final Version TESTED_VERSION = Version.valueOf("1.10.5");
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+");

  private final ContainerAdapterParams kubernetesParams;

  @Autowired
  public KubernetesOriginAdapter(AdaptersProperties adaptersProperties) {
    kubernetesParams = adaptersProperties.getKubernetes();
  }

  @Override
  public GeneralAdapterParams adapterParams() {
    return kubernetesParams;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    String detectionCommand = String.format("%s %s", kubernetesParams.getExecutable(), kubernetesParams.getVersionCommand());
    String idfString = obtainIdfString(detectionCommand);
    Assert.hasText(idfString, "kubectl returned empty version string");
    Matcher matcher = VERSION_PATTERN.matcher(idfString);
    String versionString;
    if (!matcher.find()) {
      log.warn("kubectl version string cannot be recognized: ", idfString);
      versionString = "[n/a]";
    } else {
      Version kubectlSemVer = Version.valueOf(matcher.group());

      if (kubectlSemVer.greaterThan(TESTED_VERSION)) {
        log.warn("Current kubectl version {} is greater than its tested version {}.", kubectlSemVer, TESTED_VERSION);
      }
      versionString = kubectlSemVer.toString();
    }
    log.info("Initialized Kubernetes adapter upon kubectl client v{}.", versionString);
  }
}
