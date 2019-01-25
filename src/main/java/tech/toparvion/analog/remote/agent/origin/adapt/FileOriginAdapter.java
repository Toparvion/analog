package tech.toparvion.analog.remote.agent.origin.adapt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tech.toparvion.analog.model.config.adapters.AdaptersProperties;
import tech.toparvion.analog.model.config.adapters.FileAdapterParamSection;
import tech.toparvion.analog.model.config.adapters.FileAdapterParams;
import tech.toparvion.analog.model.config.adapters.GeneralAdapterParams;

import java.util.Map;

import static java.lang.String.format;

/**
 * @author Toparvion
 * @since 0.11
 */
@Lazy
@Service
public class FileOriginAdapter extends AbstractOriginAdapter {

  private final FileAdapterParamSection fileAdapterParams;

  private GeneralAdapterParams tailParams;

  @Autowired
  public FileOriginAdapter(AdaptersProperties adaptersProperties) {
    fileAdapterParams = adaptersProperties.getFile();
  }

  @Override
  public GeneralAdapterParams adapterParams() {
    Assert.state(tailParams != null, "tailParams must be composed prior to their get method invocation");
    return tailParams;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    String detectionRequest = fileAdapterParams.getDetectionRequest();
    String idfString = obtainIdfString(detectionRequest);
    Assert.hasText(idfString, format("tail detection request '%s' hasn't returned anything", detectionRequest));
    Map<String, FileAdapterParams> tailImpls = fileAdapterParams.getTailImplementations();
    Map.Entry<String, FileAdapterParams> foundEntry = tailImpls.entrySet()
            .stream()
            .filter(entry -> idfString.startsWith(entry.getValue().getDetectionResponse()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No tail implementation parameters found " +
                                                            "for idfString:\n" + idfString));
    tailParams = foundEntry.getValue();
    log.info("Initialized file adapter upon '{}' tail implementation.", foundEntry.getKey());
  }
}
