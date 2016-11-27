package ru.ftc.upc.testing.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ftc.upc.testing.analog.model.*;
import ru.ftc.upc.testing.analog.util.Util;

import javax.servlet.http.HttpSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static ru.ftc.upc.testing.analog.service.AnaLogUtils.detectMessageType;
import static ru.ftc.upc.testing.analog.service.AnaLogUtils.distinguishXml;
import static ru.ftc.upc.testing.analog.util.Util.DEFAULT_ENCODING;
import static ru.ftc.upc.testing.analog.util.Util.DEFAULT_TITLE_FORMAT;

@RestController
public class MainController {
  private static final Logger log = LoggerFactory.getLogger(MainController.class);

  private final List<ChoiceGroup> choices;
  private final EncodingDetector encodingDetector;

  @Autowired
  public MainController(ChoicesProperties choicesProperties, EncodingDetector encodingDetector) {
    this.choices = choicesProperties.getChoices();
    this.encodingDetector = encodingDetector;
  }

  @RequestMapping("/provide")
  public Part provide(@RequestParam("log") String inputFileName,
                      @RequestParam(name = "prependingSize", required = false) Long prependingSize,
                      @RequestParam(required = false, defaultValue = "UTF-8") String encoding,
                      HttpSession session) {
    // получаем данные о предыдущем чтении
    ReadingMetaData readingMetaData = AnaLogUtils.retrieveMetaData(session, inputFileName);

    // получаем сырой набор строк из файла
    List<String> rawLines;
    try {
      rawLines = AnaLogUtils.getRawLines(inputFileName, encoding, readingMetaData, prependingSize);

    } catch (FileNotFoundException e) {
      log.warn("Ошибка при чтении заданного файла: " + e.getMessage());
      throw new RuntimeException(e);

    } catch (Exception e) {
      log.error("Internal application error: ", e);
      throw new RuntimeException(e);
    }
    if (!rawLines.isEmpty()) {
      log.trace("Raw lines read: {}", rawLines.size());
    }

    List<Line> parsedLines = new ArrayList<>();
    for (int i = 0; i < rawLines.size(); i++) {
      // проверяем строку на начало в ней XML-кода
      String curLine = distinguishXml(rawLines, i);

      // вставляем текст строки
      String text = AnaLogUtils.escapeSpecialCharacters(curLine);
      // определяем и вставляем уровень важности сообщения
      String messageType = detectMessageType(curLine);

      // завершаем оформление текущей строки
      parsedLines.add(new Line(text, messageType));
    }

    return new Part(parsedLines);
  }

  @RequestMapping("/choices")
  public List<LogChoice> choices() {
    return choices.stream()
            .flatMap(this::flattenGroup)
            .collect(toList());
  }

  private Stream<LogChoice> flattenGroup(ChoiceGroup group) {
    Set<LogChoice> choices = new LinkedHashSet<>();
    String groupName = group.getGroup();

    // first let's traverse and process all of the path entries as they are commonly used in groups
    for (String path : group.getPaths()) {
      ChoiceComponents coms = Util.extractChoiceComponents(path);
      if (coms == null) continue; // the origin of this object is responsible for logging in this case
      String title = Util.expandTitle(coms.getPurePath(), coms.getPureTitle(), groupName);
      String fullPath = group.getPathBase() + coms.getPurePath();
      String encoding = encodingDetector.getEncodingFor(fullPath, DEFAULT_ENCODING);
      choices.add(new LogChoice(groupName,
              fullPath,
              encoding,
              title,
              coms.isSelectedByDefault()));
    }

    // then let's add scanned directory logs to set being composed
    if (group.getScanDir() != null) {
      String groupEncoding = (group.getEncoding() != null)
              ? Util.formatEncodingName(group.getEncoding())
              : null;   // this value will provoke encoding detection
      Path scanDirPath = Paths.get(group.getScanDir());
      try (Stream<Path> scannedPaths = Files.list(scanDirPath)) {
        choices.addAll(scannedPaths   // such sets merging allows to exclude duplicates while preserving explicit paths
                .filter(Files::isRegularFile)   // the scanning is not recursive so we bypass nested directories
                .map(logPath -> new LogChoice(groupName,
                        logPath.toAbsolutePath().toString(),
                        (groupEncoding != null)
                                ? groupEncoding
                                : encodingDetector.getEncodingFor(logPath.toAbsolutePath().toString(), DEFAULT_ENCODING),
                        Util.expandTitle(logPath.toString(), DEFAULT_TITLE_FORMAT, groupName),
                        false))
                .collect(toSet()));
      } catch (IOException e) {
        log.error(format("Failed to scan directory '%s'; will be ignored.", group.getScanDir()), e);
      }
    }

    return choices.stream();
  }
}