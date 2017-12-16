package tech.toparvion.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.w3c.tidy.Tidy;
import tech.toparvion.analog.model.RecordLevel;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static tech.toparvion.analog.model.RecordLevel.PLAIN;

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

  private static final Pattern WIN_DISC_PATTERN = Pattern.compile("^\\w:");

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
      public void write(String s) {
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
    return "PLAIN";
  }

  @Nonnull
  public static RecordLevel detectRecordLevel(Message<String> recordMessage) {
    String recordLine = recordMessage.getPayload();
    return Stream.of(RecordLevel.values())
        .filter(level -> !PLAIN.equals(level))
        .filter(level -> recordLine.contains(level.name()))    // this is potential subject to change in future
        .findAny()
        .orElse(PLAIN);
  }

  public static String normalizePath(String pathToNormalize) {
    // in case of working on Windows the path needs to be formatted to Linux style
    String result = pathToNormalize.replaceAll("\\\\", "/");
    if (WIN_DISC_PATTERN.matcher(result).find()) {
      result = "/" + result;
    }
    return result;
  }
}
