package tech.toparvion.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;
import tech.toparvion.analog.model.config.entry.LogPath;
import tech.toparvion.analog.model.config.entry.LogType;

import javax.annotation.Nonnull;

import static java.lang.String.format;
import static tech.toparvion.analog.model.config.entry.LogType.COMPOSITE;
import static tech.toparvion.analog.model.config.entry.LogType.NODE;
import static tech.toparvion.analog.util.PathUtils.*;

/**
 * @author Toparvion
 * @since v0.11
 */
@Service
@ConfigurationPropertiesBinding
public class String2PlainEntryConverter implements Converter<String, LogPath> {
  private static final Logger log = LoggerFactory.getLogger(String2PlainEntryConverter.class);

  private final String thisNodeName;

  @Autowired
  public String2PlainEntryConverter(@Value("${nodes.this.name}") String thisNodeName) {
    this.thisNodeName = thisNodeName;
  }

  /**
   * Converts given path into corresponding Java object representation, i.e. parses the path and extracts all its
   * meaningful composing parts.<p>
   * Suitable for invocation both by application code and by the framework during configuration reading.
   *
   * @param rawPath a path to parse and convert
   * @return parsed log path
   * @throws IllegalArgumentException if provided log type doesn't match this converter
   */
  @Override
  @Nonnull      // because current implementation is not allowed to return null
  @SuppressWarnings("NullableProblems")
  public LogPath convert(String rawPath) throws IllegalArgumentException {
    // first, let's remove the leading 'artificial' slashes (if any) from the path
    String unleadedPath = removeLeadingSlash(rawPath);
    // then convert backward slashes to forward
    String convertedPath = convertToUnixStyle(unleadedPath);
    LogType logType = LogType.detectFor(convertedPath);

    if (logType == COMPOSITE) {
      throw new IllegalArgumentException(format("Composite log path '%s' must not be passed " +
          "to plain log path processing.", unleadedPath));
    }

    LogPath logPath = new LogPath();
    logPath.setType(logType);

    if (logType == NODE) {          // paths pointing to specific node require special handling
      // obtain path without 'node://' prefix, e.g. 'angara/home/upc/app.log'
      int schemaSeparatorIndex = convertedPath.indexOf(CUSTOM_SCHEMA_SEPARATOR);
      var nodefulPath = convertedPath.substring(schemaSeparatorIndex + CUSTOM_SCHEMA_SEPARATOR.length());
      int nodeSeparatorIndex = nodefulPath.indexOf('/');
      // extract node name, e.g. 'angara'
      var detectedNode = nodefulPath.substring(0, nodeSeparatorIndex);
      // ...and the path itself (within the node), e.g. '/home/upc/app.log'
      var cleanPath = nodefulPath.substring(nodeSeparatorIndex);
      // a tricky moment: in case of Windows path we must strip the leading slash, so we detect it by presence of colon
      if (cleanPath.indexOf(':') == 2) {      // e.g. /c:/windows/folder
        cleanPath = cleanPath.substring(1);
      }
      logPath.setTarget(cleanPath);
      logPath.setFullPath(convertedPath);
      logPath.setNode(detectedNode);

    } else if (convertedPath.contains(CUSTOM_SCHEMA_SEPARATOR)) {        // any other custom path
      int schemaSeparatorIndex = convertedPath.indexOf(CUSTOM_SCHEMA_SEPARATOR);
      var target = convertedPath.substring(schemaSeparatorIndex + CUSTOM_SCHEMA_SEPARATOR.length());
      logPath.setTarget(target);
      logPath.setFullPath(convertedPath);
      logPath.setNode(thisNodeName);

    } else {                                                            // vanilla file path (on local FS)
      logPath.setTarget(convertedPath);
      logPath.setFullPath(convertedPath);
      // the preceding two values can be changed further by path base prepending, see ChoiceProperties#tuneProperties()
      logPath.setNode(thisNodeName);
    }
    log.debug("String '{}' has been parsed into: {}", rawPath, logPath);
    return logPath;
  }


}
