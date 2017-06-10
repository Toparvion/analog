package ru.ftc.upc.testing.analog.service;

import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ftc.upc.testing.analog.model.config.ChoiceGroup;
import ru.ftc.upc.testing.analog.model.config.ChoiceProperties;
import ru.ftc.upc.testing.analog.model.config.LogConfigEntry;
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
  private static final String DEFAULT_ENCODING = "UTF-8";

  private final ConcurrentHashMap<String, String> detectedEncodings;

  @Autowired
  public EncodingDetector(ChoiceProperties properties) {
    detectedEncodings = new ConcurrentHashMap<>(properties.getChoices().size());
    long wholeProcessingStart = System.currentTimeMillis();
    properties.getChoices().stream()
            .filter(group -> (group.getEncoding()==null))
            .parallel()
            .forEach(this::processGroup);
    log.info("The whole encodings detection took {} ms.", (System.currentTimeMillis()-wholeProcessingStart));
  }

  private void processGroup(ChoiceGroup group) {
    long groupProcessingStart = System.currentTimeMillis();
    for (LogConfigEntry logConfigEntry : group.getCompositeLogs()) {
      if (logConfigEntry.getEncoding() != null) {
        log.trace("Log entry '{}' has encoding explicitly set. Skip.", logConfigEntry.getPath());
        continue;
      }
      String path = group.getPathBase() + logConfigEntry.getPath();
      processPath(path);
    }
    log.debug("Group '{}' has been processed for {} ms.", group.getGroup(),
                                                          (System.currentTimeMillis() - groupProcessingStart));
  }

  private void processPath(String path) {
    File file = new File(path);
    try {
      long fileProcessingStart = System.currentTimeMillis();
      String encoding = UniversalDetector.detectCharset(file);
      if (encoding != null) {
        if (encoding.toUpperCase().contains("CYRILLIC"))
          encoding = "WINDOWS-1251";      // dirty hack to avoid detection mistakes
        detectedEncodings.put(path, Util.formatEncodingName(encoding));
        log.debug("Encoding of log '{}' detected as '{}' (took {} ms).", path, encoding, (System.currentTimeMillis() - fileProcessingStart));
      } else {
        detectedEncodings.put(path, DEFAULT_ENCODING);
        log.warn("UniversalCharDet couldn't recognize the encoding of log '{}'; {} has been selected as default. " +
                "(took {} ms).", path, DEFAULT_ENCODING, (System.currentTimeMillis() - fileProcessingStart));
      }

    } catch (IOException e) {
      detectedEncodings.put(path, DEFAULT_ENCODING);
      log.warn(format("Couldn't detect encoding of log '%s' because of error. %s has been selected as default",
              path, DEFAULT_ENCODING), e);
    }
  }

  String getEncodingFor(String path) {
    String cachedEncoding = detectedEncodings.get(path);
    if (cachedEncoding != null) {
      log.trace("Cache hit: {} for log '{}' ", cachedEncoding, path);
      return cachedEncoding;
    }

    log.trace("Cache miss for log '{}'; attempting to detect encoding...", path);
    processPath(path);

    return detectedEncodings.getOrDefault(path, DEFAULT_ENCODING);
  }
}
