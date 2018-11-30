package tech.toparvion.analog.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.tidy.Tidy;
import tech.toparvion.analog.model.config.ChoiceGroup;
import tech.toparvion.analog.model.config.LogConfigEntry;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasText;
import static tech.toparvion.analog.remote.RemotingConstants.PLAIN_RECORD_LEVEL_NAME;
import static tech.toparvion.analog.util.PathConstants.CUSTOM_SCHEMA_SEPARATOR;

/**
 * Created with IntelliJ IDEA.
 * Date: 02.10.14
 * Time: 9:58
 */
public class AnaLogUtils {
  private static final Logger log = LoggerFactory.getLogger(AnaLogUtils.class);
  private static final Logger tidyLog = LoggerFactory.getLogger(Tidy.class);
  // шаблоны разбора
  private static final Pattern MESSAGE_LEVEL_EXTRACTOR = Pattern.compile("^[\\S ]*(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)");
  private static final Pattern XML_OPEN_EXTRACTOR = Pattern.compile("<((?:\\w[\\w-]*:)?\\w[\\w-]*).*>");
  private static final Pattern WHOLE_XML_EXTRACTOR = Pattern.compile("^<((?:\\w[\\w-]*:)?\\w[\\w-]*).*>.*</\\1>$", Pattern.DOTALL);

  public static String escapeSpecialCharacters(String inputString) {
    String result = inputString.replaceAll("\"", "&quot;");
    result = result.replaceAll("<", "&lt;");
    result = result.replaceAll(">", "&gt;");
    result = result.replaceAll("\r?\n", "\n");
    return result;
  }

  // TODO move the method to RecordSender or its harness
  public static String distinguishXml(List<String> rawLines, int startingLineIndex) {
    String startingLine = rawLines.get(startingLineIndex);
    Matcher xmlOpenMatcher = XML_OPEN_EXTRACTOR.matcher(startingLine);
    if (!xmlOpenMatcher.find()) {
      return startingLine;
    }
    String openTagName = xmlOpenMatcher.group(1);
    int startPositionForXmlCloseSearch = xmlOpenMatcher.end(1);
    Pattern xmlCloseExtractor = Pattern.compile("</" + openTagName + ">");

    // пытаемся отдельно обработать ПЕРВУЮ СТРОКУ
    Matcher xmlCloseMatcher = xmlCloseExtractor.matcher(startingLine);
    if (xmlCloseMatcher.find(startPositionForXmlCloseSearch)) {            // значит, весь XML "упрятан" в одной строке
      int xmlOpenIndex = xmlOpenMatcher.start(1) - 1;
      int xmlCloseIndex = xmlCloseMatcher.end();
      String xml = startingLine.substring(xmlOpenIndex, xmlCloseIndex);
      xml = reindentXml(xml);
      boolean lineHasNonXmlBeginning = (xmlOpenIndex != 0);
      boolean lineHasNonXmlEnding = (xmlCloseIndex < startingLine.length());
      if (lineHasNonXmlEnding) {
        String nonXmlEnding = startingLine.substring(xmlCloseIndex, startingLine.length());
        rawLines.add(startingLineIndex + 1, nonXmlEnding);
      }
      if (lineHasNonXmlBeginning) {
        String nonXmlBeginning = startingLine.substring(0, xmlOpenIndex);
        rawLines.set(startingLineIndex, nonXmlBeginning);
        rawLines.add(startingLineIndex + 1, xml);
      }
      if (!lineHasNonXmlBeginning && !lineHasNonXmlEnding) {
        rawLines.set(startingLineIndex, xml);
      }
      return rawLines.get(startingLineIndex);
    }
    // дочитываем ХВОСТ ДОКУМЕНТА
    int i = startingLineIndex + 1;
    boolean isXmlCloseTagFound = false;
    StringBuilder accumulator = new StringBuilder(startingLine);
    while (i < rawLines.size()) {
      String curLine = rawLines.get(i);
      Matcher constrainMatcher = MESSAGE_LEVEL_EXTRACTOR.matcher(curLine);
      if (constrainMatcher.find()) {
        break;
      }
      accumulator.append(curLine);
      xmlCloseMatcher = xmlCloseExtractor.matcher(curLine);
      if (xmlCloseMatcher.find()) {
        isXmlCloseTagFound = true;
        break;
      }
      i++;
    }
    // удостоверяемся в успешности поиска
    if (!isXmlCloseTagFound) {
      return startingLine;
    }
    // если найден закрывающий тег
    // (1) удаляем старое "растянутое" представление XML
    for (int j = startingLineIndex + 1; j <= i; j++) {
      rawLines.remove(startingLineIndex + 1);
    }
    // (2) подготавливаем XML-строку к вставке и вставляем ее
    String accumulatedString = accumulator.toString();
    int xmlOpenIndex = xmlOpenMatcher.start(1) - 1;
    int xmlCloseIndex = accumulatedString.lastIndexOf(xmlCloseMatcher.group()) + xmlCloseMatcher.group().length();
    boolean lineHasNonXmlBeginning = (xmlOpenIndex != 0);
    boolean lineHasNonXmlEnding = (xmlCloseIndex < accumulatedString.length());
    if (lineHasNonXmlEnding) {
      String nonXmlEnding = accumulatedString.substring(xmlCloseIndex, accumulatedString.length());
      rawLines.add(startingLineIndex + 1, nonXmlEnding);
      accumulatedString = accumulatedString.substring(0, xmlCloseIndex);
    }
    if (lineHasNonXmlBeginning) {
      String nonXmlBeginning = accumulatedString.substring(0, xmlOpenIndex);
      rawLines.set(startingLineIndex, nonXmlBeginning);
      accumulatedString = accumulatedString.substring(xmlOpenIndex, accumulatedString.length());
      accumulatedString = reindentXml(accumulatedString);
      rawLines.add(startingLineIndex + 1, accumulatedString);
    } else {
      accumulatedString = reindentXml(accumulatedString);
      rawLines.set(startingLineIndex, accumulatedString);
    }

    return rawLines.get(startingLineIndex);
  }

  // TODO move the method to RecordSender or its harness
  public static String distinguishXmlComposite(List<String> rawLines, int startingLineIndex) {
    String startingLine = rawLines.get(startingLineIndex);
    if (startingLine.startsWith("__XML__")) {   // if the line marker as XML before
      return startingLine;
    }
    Matcher xmlOpenMatcher = XML_OPEN_EXTRACTOR.matcher(startingLine);
    if (!xmlOpenMatcher.find()) {
      return startingLine;
    }
    String openTagName = xmlOpenMatcher.group(1);
    int startPositionForXmlCloseSearch = xmlOpenMatcher.end(1);
    Pattern xmlCloseExtractor = Pattern.compile("</" + openTagName + ">");

    // пытаемся отдельно обработать ПЕРВУЮ СТРОКУ
    Matcher xmlCloseMatcher = xmlCloseExtractor.matcher(startingLine);
    if (xmlCloseMatcher.find(startPositionForXmlCloseSearch)) {            // значит, весь XML "упрятан" в одной строке
      int xmlOpenIndex = xmlOpenMatcher.start(1) - 1;
      int xmlCloseIndex = xmlCloseMatcher.end();
      String xml = startingLine.substring(xmlOpenIndex, xmlCloseIndex);
      xml = reindentXml(xml);
      xml = prefix(xml);      // to mark the start of XML string among other records' strings
      boolean lineHasNonXmlBeginning = (xmlOpenIndex != 0);
      boolean lineHasNonXmlEnding = (xmlCloseIndex < startingLine.length());
      if (lineHasNonXmlEnding) {
        String nonXmlEnding = startingLine.substring(xmlCloseIndex, startingLine.length());
        rawLines.add(startingLineIndex + 1, nonXmlEnding);
      }
      if (lineHasNonXmlBeginning) {
        String nonXmlBeginning = startingLine.substring(0, xmlOpenIndex);
        rawLines.set(startingLineIndex, nonXmlBeginning);
        rawLines.add(startingLineIndex + 1, xml);
      }
      if (!lineHasNonXmlBeginning && !lineHasNonXmlEnding) {
        rawLines.set(startingLineIndex, xml);
      }
      return rawLines.get(startingLineIndex);
    }
    // дочитываем ХВОСТ ДОКУМЕНТА
    int i = startingLineIndex + 1;
    boolean isXmlCloseTagFound = false;
    StringBuilder accumulator = new StringBuilder(startingLine);
    while (i < rawLines.size()) {
      String curLine = rawLines.get(i);
      accumulator.append(curLine);
      xmlCloseMatcher = xmlCloseExtractor.matcher(curLine);
      if (xmlCloseMatcher.find()) {
        isXmlCloseTagFound = true;
        break;
      }
      i++;
    }
    // удостоверяемся в успешности поиска
    if (!isXmlCloseTagFound) {
      if (log.isTraceEnabled()) {
        log.trace("No XML close tag found for document starting with '{}'.", accumulator);
      } else {
        log.debug("No XML close tag found for document starting with '{}'. Skipped.", startingLine);
      }
      return startingLine;
    }
    // если найден закрывающий тег
    // (1) удаляем старое "растянутое" представление XML
    for (int j = startingLineIndex + 1; j <= i; j++) {
      rawLines.remove(startingLineIndex + 1);
    }
    // (2) подготавливаем XML-строку к вставке и вставляем ее
    String accumulatedString = accumulator.toString();
    int xmlOpenIndex = xmlOpenMatcher.start(1) - 1;
    int xmlCloseIndex = accumulatedString.lastIndexOf(xmlCloseMatcher.group()) + xmlCloseMatcher.group().length();
    boolean lineHasNonXmlBeginning = (xmlOpenIndex != 0);
    boolean lineHasNonXmlEnding = (xmlCloseIndex < accumulatedString.length());
    if (lineHasNonXmlEnding) {
      String nonXmlEnding = accumulatedString.substring(xmlCloseIndex, accumulatedString.length());
      rawLines.add(startingLineIndex + 1, nonXmlEnding);
      accumulatedString = accumulatedString.substring(0, xmlCloseIndex);
    }
    if (lineHasNonXmlBeginning) {
      String nonXmlBeginning = accumulatedString.substring(0, xmlOpenIndex);
      rawLines.set(startingLineIndex, nonXmlBeginning);
      accumulatedString = accumulatedString.substring(xmlOpenIndex, accumulatedString.length());
      accumulatedString = reindentXml(accumulatedString);
      accumulatedString = prefix(accumulatedString);

      rawLines.add(startingLineIndex + 1, accumulatedString);
    } else {
      accumulatedString = reindentXml(accumulatedString);
      accumulatedString = prefix(accumulatedString);
      rawLines.set(startingLineIndex, accumulatedString);
    }

    return rawLines.get(startingLineIndex);
  }

  private static String prefix(String stringToPrefix) {
    return "__XML__" + stringToPrefix;
  }

  private static String reindentXml(String rawSourceXml) {
    Tidy tidy = new Tidy();
    StringWriter outputWriter = new StringWriter();
    tidy.setOutputEncoding("UTF-8");
    tidy.setXmlTags(true);
    tidy.setXmlOut(true);
    tidy.setIndentAttributes(false);
    // tidy.setSmartIndent(true); // excess
    tidy.setWraplen(150);
    tidy.setWrapAttVals(true);
    tidy.setErrout(new PrintWriter(new StringWriter() /* <- just a stub, isn't used actually */) {
      @Override
      public void write(@SuppressWarnings("NullableProblems") String s) {
        tidyLog.info(s);      // we consider Tidy as auxiliary component so that its warnings are info for us
      }
    });
    // tidy.setOnlyErrors(true);    // leads to undesirable Tidy behavior and therefore is turned off

    final StringReader inputReader = new StringReader(rawSourceXml);
    tidy.parse(inputReader, outputWriter);
    String prettyPrintedSourceXml = outputWriter.toString();
    inputReader.close();
    if ("".equals(prettyPrintedSourceXml)) {
      log.warn(String.format("Failed to pretty print XML started with '%s'. Falling back to raw string.",
              rawSourceXml.substring(0, Math.max(rawSourceXml.length(), 19))));
      return rawSourceXml;
    }
    else return prettyPrintedSourceXml;
  }

  public static String detectMessageType(String curLine) {
    Matcher levelMatcher = MESSAGE_LEVEL_EXTRACTOR.matcher(curLine);
    if (levelMatcher.find()) {
      return levelMatcher.group(1);
    }
    Matcher xmlMatcher = WHOLE_XML_EXTRACTOR.matcher(curLine);
    if (xmlMatcher.find()) {
      return "XML";
    }
    return PLAIN_RECORD_LEVEL_NAME;
  }

  /**
   * Converts given path to Unix style, e.g. {@code C:\lang\analog} into {@code /C:/lang/analog}. The resulting path
   * may or may not be prepended with a slash depending on {@code prependWithSlash} parameter. Usually the slash is
   * required for sending the path to web client and not required when handling the path on server side.
   * @param path             path to convert
   * @param prependWithSlash whether adding of leading slash is required or not; won't have any effect either if the
   *                         path already has a leading slash or doesn't contain a semicolon
   * @return                 converted path
   */
  public static String convertToUnixStyle(String path, boolean prependWithSlash) {
    // in case of working on Windows the path needs to be formatted to Unix style
    String result = path.replaceAll("\\\\", "/");
    if (!prependWithSlash) {
      return result;
    }
    if (!result.startsWith("/") && result.contains(":")) {
      result = "/" + result;  // also prepend it with slash to make absolute paths identical on *nix and Windows systems
    }
    return result;
  }

  /**
   * Removes given path's first character if it is forward slash '/' and the path contains a colon ':' (i.e. the path
   * is not ordinary absolute Unix path). In case of two slashes at the very beginning of the path, removes one of
   * them unconditionally.
   * This usually needed for paths came from web client.
   * @param path a path to preprocess
   * @return the same path without leading slash
   */
  public static String removeLeadingSlash(String path) {
    // if for some reason path starts with double slash it must be reduced to single slash before next check
    if (path.startsWith("//")) {
      path = path.substring(1 );
    }
    return path.startsWith("/") && path.contains(":")
        ? path.substring(1)   // to omit 'artificial' leading slash added for correct handling on frontend
        : path;

  }

  /**
   * Checks if given path points to a file located on current machine's file system, i.e. if it a usual path like
   * {@code /home/user/app.log} or {@code C:/Users/user/app.log} AND NOT a custom schema prefixed path like
   * {@code docker://container} or {@code /node://remote/home/user/app.log}.<p>
   * <strong>CAUTION:</strong> This methods relies on clean paths only in a sense that there MUST NOT be any leading slash
   * (usually came from by web client). So that any path received from the client and looking like
   * {@code /k8s://deploy ...} must be {@linkplain #removeLeadingSlash(String) preprocessed and shorten} to form like
   * {@code k8s://deploy...} (without leading slash).
   * @param path path to check
   * @return {@code false} if given path contains {@link PathConstants#CUSTOM_SCHEMA_SEPARATOR custom schema separator}
   * and {@code true} otherwise
   */
  public static boolean isLocalFilePath(String path) {
    return path.indexOf(CUSTOM_SCHEMA_SEPARATOR, 1) == -1;
  }

  /**
   * Returns a file name denoted by given {@code path}. If given a {@linkplain #isLocalFilePath(String) local file
   * path}, detects the name as substring after the latest forward slash. Consequently, implies that the path has been
   * already {@linkplain #convertToUnixStyle(String, boolean) converted} to Unix style. If given a local file path,
   * relies on {@link Path} functionality to extract the name.
   * @param path path to extract file name from
   * @return file name denoted by given path
   */
  public static String extractFileName(String path) {
    if (!isLocalFilePath(path)) {
      int lastSlashIndex = path.lastIndexOf('/');
      return path.substring(lastSlashIndex+1);

    } else {
      return Paths.get(path)
              .getFileName()
              .toString();
    }
  }

  public static String nvls(String s, String def) {
    return (s == null || "".equals(s))
            ? def
            : s;
  }

  /**
   * Prevents {@link Throwable}s from being thrown outside this method. Intended for use when performing some
   * error-prone action must not interrupt further steps from being taken. It is actually equal to {@code
   * try/finally} statement but more flexible and compact.
   * @implNote The class of exception is deliberately escalated to Throwable to account cases when e.g.
   * {@link AssertionError} are thrown.
   * @param callerClass class of calling object to use for writing down an exception on behalf of
   * @param action faulty action
   * @return exception happened (if any) for custom processing
   */
  public static Optional<Throwable> doSafely(Class<?> callerClass, SafeAction action) {
    try {
      action.act();
      return Optional.empty();

    } catch (Throwable e) {
      LoggerFactory.getLogger(callerClass).error("Failed to perform action.", e);
      return Optional.of(e);
    }
  }

  /**
   * Corrects paths in composite logs entries in a way that accounts a path base specified on group level. I.e. if a
   * group contains a non-empty path base then this method will prepend every composite log's path with that path base.
   * There is an exclusion though - if a log's own path is an absolute one, it won't be prepended with group path base.
   * This prepending allows further logic to work with log config entries only, without referring to their containing
   * groups.
   * @param group choice group to process
   */
  public static void applyPathBase(ChoiceGroup group) {
    String pathBase = group.getPathBase();
    if (hasText(pathBase)) {
      Path base = Paths.get(pathBase);
      assert base.isAbsolute() : format("'pathBase' parameter %s is not absolute", pathBase);
      for (LogConfigEntry compositeLog : group.getCompositeLogs()) {
        Path entryOwnPath = Paths.get(compositeLog.getPath());
        if (!entryOwnPath.isAbsolute()) {
          Path entryFullPath = base.resolve(entryOwnPath);
          log.debug("Changed log config entry's path from '{}' to '{}'.", entryOwnPath, entryFullPath);
          compositeLog.setPath(entryFullPath.toAbsolutePath().toString());
        } else {
          log.debug("Log path '{}' is already absolute and thus won't be prepended with base.", entryOwnPath);
        }
      }

    }
  }

  @FunctionalInterface
  public interface SafeAction {
    void act() throws Throwable;
  }
}
