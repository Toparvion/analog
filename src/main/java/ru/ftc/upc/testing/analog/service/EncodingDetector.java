package ru.ftc.upc.testing.analog.service;

import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.model.ChoiceComponents;
import ru.ftc.upc.testing.analog.model.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.ChoicesProperties;
import ru.ftc.upc.testing.analog.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

/**
 * @author Toparvion
 */
@Service
public class EncodingDetector {
  private static final Logger log = LoggerFactory.getLogger(EncodingDetector.class);

  private final ConcurrentHashMap<String, String> detectedEncodings;

  @Autowired
  public EncodingDetector(ChoicesProperties properties) {
    detectedEncodings = new ConcurrentHashMap<>(properties.getChoices().size());
    long wholeProcessingStart = System.currentTimeMillis();
    properties.getChoices().stream()
            .filter(group -> (group.getEncoding()==null))
            .parallel()
            .forEach(this::processGroup);
    log.info("Encodings detection took {} ms.", (System.currentTimeMillis()-wholeProcessingStart));
  }

  private void processGroup(ChoiceGroup group) {
    long groupProcessingStart = System.currentTimeMillis();
    for (String pathSpec : group.getPaths()) {
      ChoiceComponents coms = Util.extractChoiceComponents(pathSpec);
      if (coms == null) continue;   // the origin of this object is responsible for logging in this case
      String path = group.getPathBase() + coms.getPurePath();
      File file = new File(path);
      try {
        long fileProcessingStart = System.currentTimeMillis();
        String encoding = UniversalDetector.detectCharset(file);
        if (encoding != null) {
          detectedEncodings.put(path, Util.formatEncodingName(encoding));
          log.debug("Encoding of log '{}' detected as '{}' (took {} ms).", path, encoding, (System.currentTimeMillis() - fileProcessingStart));
        } else {
          log.warn("UniversalCharDet couldn't recognize the encoding of log '{}' (took {} ms).", path, (System.currentTimeMillis() - fileProcessingStart));
        }

      } catch (IOException e) {
        log.warn(format("Couldn't detect encoding of log '%s' because of error.", path), e);
      }
    }
    log.debug("Group '{}' has been processed for {} ms.", group.getGroup(),
            (System.currentTimeMillis() - groupProcessingStart));
  }

  public ConcurrentHashMap<String, String> getDetectedEncodings() {
    return detectedEncodings;
  }
}
