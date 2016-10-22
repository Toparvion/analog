package ru.ftc.upc.testing.analog.service;

import org.apache.log4j.Logger;
import org.w3c.tidy.Tidy;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Created with IntelliJ IDEA.
 * Date: 02.10.14
 * Time: 9:58
 */
public class AnaLogUtils {
  private static final Logger logger = Logger.getLogger(AnaLogUtils.class);
  // шаблоны разбора
  private static final Pattern MESSAGE_LEVEL_EXTRACTOR = Pattern.compile("^[\\S ]*(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)");
  private static final Pattern XML_OPEN_EXTRACTOR = Pattern.compile("<((?:\\w[\\w-]*:)?\\w[\\w-]*).*>");
  private static final Pattern WHOLE_XML_EXCTRACTOR = Pattern.compile("^<((?:\\w[\\w-]*:)?\\w[\\w-]*).*>.*</\\1>$", Pattern.DOTALL);
  // общие настройки
  private static final long SHOWN_LOG_MAX_SIZE = 32768L;
  private static final int LINE_SEPARATOR_LENGTH = System.getProperty("line.separator").length();

  public static String escapeSpecialCharacters(String inputString) {
    String result = inputString.replaceAll("\"", "&quot;");
    result = result.replaceAll("<", "&lt;");
    result = result.replaceAll(">", "&gt;");
    result = result.replaceAll("\r?\n", "\\\\n");
    return result;
  }

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

  private static String reindentXml(String rawSourceXml) {
    Tidy tidy = new Tidy();
    StringWriter outputWriter = new StringWriter();
    tidy.setOutputEncoding("UTF-8");
    tidy.setXmlTags(true);
    tidy.setXmlOut(true);
    tidy.setIndentAttributes(false);
//    tidy.setSmartIndent(true);
    tidy.setWraplen(150);
    tidy.setWrapAttVals(true);
    final StringReader inputReader = new StringReader(rawSourceXml);
    tidy.parse(inputReader, outputWriter);
    String prettyPrintedSourceXml = outputWriter.toString();
    inputReader.close();
    if ("".equals(prettyPrintedSourceXml)) {
      logger.warn(String.format("Failed to pretty print XML started with '%s'. Falling back to raw string.",
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
    Matcher xmlMatcher = WHOLE_XML_EXCTRACTOR.matcher(curLine);
    if (xmlMatcher.find()) {
      return "XML";
    }
    return "UNKNOWN";
  }

  public static List<String> getRawLines(String inputFileName,
                                         String encoding, ReadingMetaData readingMetaData,
                                         Long prependingSnippetSizePercent) throws Exception {
    // проверяем наличие указанного файла
    File inputFile = new File(inputFileName);
    if (!inputFile.exists() || !inputFile.isFile()) {
      throw new FileNotFoundException("Файл '" + inputFileName + "' не найден.");
    }

    // флаг режима предварения (противоположность дополнению)
    boolean prependingMode = (prependingSnippetSizePercent != null);

    long fileActualSize = inputFile.length();
    long fileSavedSize = readingMetaData.getFileSavedSize();
    // проверяем, изменился ли файл с момента последнего чтения
    if ((fileActualSize == fileSavedSize) && !prependingMode) {
      return Collections.emptyList();
    }
    readingMetaData.setFileSavedSize(fileActualSize);

    // заготавливаем массив для хранения прочтенных строк
    List<String> rawLines = new ArrayList<String>();

    if (prependingMode) {                                                       // чтение к началу
      // определяем позицию, с которой необходимо начать чтение
      long prependingCounter;
      if (!readingMetaData.isPrependingCounterSet()) {
        prependingCounter = (fileActualSize - SHOWN_LOG_MAX_SIZE) > 0L
                ? (fileActualSize - SHOWN_LOG_MAX_SIZE)
                : 0L;
        logger.warn("Prepending counter was not initialized; has been computed as " + prependingCounter);
      } else {
        prependingCounter = readingMetaData.getPrependingCounter();
      }

      long prependingSnippetSizeChars = Math.round((prependingSnippetSizePercent / 100d) * fileActualSize);
      long readStartPosition = prependingCounter - prependingSnippetSizeChars;
      if (readStartPosition < 0) {
        readStartPosition = 0;
        rawLines.add("(достигнуто начало документа)");
      }

      // приступаем к чтению
      BufferedReader bufferedReader =
              new BufferedReader(
                      new InputStreamReader(
                              new FileInputStream(inputFile),
                              encoding));
      bufferedReader.skip(readStartPosition);

      String line;
      long readCurrentPosition = readStartPosition;

      while ((line = bufferedReader.readLine()) != null) {
        readCurrentPosition += (line.length() + LINE_SEPARATOR_LENGTH);
        rawLines.add(line);
        if (readCurrentPosition > prependingCounter) {
          if (prependingCounter < (line.length() + LINE_SEPARATOR_LENGTH)) {
            rawLines.remove(0);
//            rawLines.add(0, "(достигнуто начало документа)");
          }
          break;
        }
      }
      bufferedReader.close();
      readingMetaData.setPrependingCounter(readStartPosition);

    } else {                                                                    // чтение к концу
      long readCharsCounter = readingMetaData.getAppendingCounter();
      if ((fileActualSize - readCharsCounter) > SHOWN_LOG_MAX_SIZE) {
        // ограничиваем чтение лога значением SHOWN_LOG_MAX_SIZE байт с конца
        readCharsCounter = fileActualSize - SHOWN_LOG_MAX_SIZE;
//        rawLines.add("...");
      } else if (readCharsCounter > fileActualSize) {
        readCharsCounter = 0L;
      }

      // запоминаем позицию начала чтения, если не делали этого раньше
      if (!readingMetaData.isPrependingCounterSet()) {
        readingMetaData.setPrependingCounter(readCharsCounter);
      }

      FileInputStream fis = new FileInputStream(inputFile);
      long skip = fis.skip(readCharsCounter);
      logger.debug(format("Required skip value: %d, actual skip value: %d, difference: %d", readCharsCounter, skip, (readCharsCounter-skip)));
      Scanner scanner = new Scanner(fis, encoding);
      while (scanner.hasNextLine()) {
        String nextLine = scanner.nextLine();
        rawLines.add(nextLine);
        readCharsCounter += (nextLine.length() + LINE_SEPARATOR_LENGTH);
      }
      scanner.close();
      readCharsCounter -= LINE_SEPARATOR_LENGTH;
      readingMetaData.setAppendingCounter(readCharsCounter);
    }

    return rawLines;
  }

  public static String nvls(String s, String def) {
    return (s == null || "".equals(s))
            ? def
            : s;
  }

  public static void closeWebSocket(Session session, CloseReason.CloseCodes code, String message) {
    try {
      session.close(new CloseReason(code, message));

    } catch (IOException e) {
      logger.error("Не получилось закрыть сессию webSocket'а: ", e);
    }
  }

  public static ReadingMetaData retrieveMetaData(HttpSession session, String inputFileName) {
    // восстанавливаем данные о предыдущем чтении
    ReadingMetaData readingMetaData = (ReadingMetaData) session.getAttribute(inputFileName);
    if (readingMetaData == null) {
      readingMetaData = new ReadingMetaData();
      session.setAttribute(inputFileName, readingMetaData);
    }
    return readingMetaData;
  }

  public static void writeResponse(StringBuilder responseBuilder, List<String> rawLines) throws IOException {
    // response.setCharacterEncoding("UTF-8");
    // оформляем начало ответа
    responseBuilder.append("{");
    responseBuilder.append("\"items\": [");

    for (int i = 0; i < rawLines.size(); i++) {
      responseBuilder.append("{");
      // проверяем строку на начало в ней XML-кода
      String curLine = distinguishXml(rawLines, i);

      // вставляем текст строки
      responseBuilder.append("\"text\": \"")
              .append(escapeSpecialCharacters(curLine))
              .append("\"");

      // определяем и вставляем уровень важности сообщения
      String messageType = detectMessageType(curLine);
      responseBuilder.append(", \"level\": \"")
              .append(messageType)
              .append("\"");
      // завершаем JSON-оформление текущей строки
      if (i == (rawLines.size() - 1))
        responseBuilder.append("}");
      else
        responseBuilder.append("},");
    }
    // оформляем конец ответа
    responseBuilder.append("]");
    responseBuilder.append("}");
  }
}
