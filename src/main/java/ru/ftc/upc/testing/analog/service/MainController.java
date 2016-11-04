package ru.ftc.upc.testing.analog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ftc.upc.testing.analog.model.Line;
import ru.ftc.upc.testing.analog.model.LogChoice;
import ru.ftc.upc.testing.analog.model.Part;

import javax.servlet.http.HttpSession;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static ru.ftc.upc.testing.analog.service.AnaLogUtils.detectMessageType;
import static ru.ftc.upc.testing.analog.service.AnaLogUtils.distinguishXml;

@RestController
public class MainController {
  private static final Logger log = LoggerFactory.getLogger(MainController.class);

  @RequestMapping("/provide")
  public Part provide(@RequestParam("log") String inputFileName,
                      @RequestParam(name = "prependingSize", required = false) Long prependingSize,
                      @RequestParam(required = false, defaultValue = "UTF-8") String encoding,
                      HttpSession session) {
    log.info("Session id: {}", (session == null) ? "(n/a)" : session.getId());
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
    log.debug("Raw lines read: {}", rawLines.size());

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
  public List<LogChoice> choices() throws InterruptedException {
    List<LogChoice> choices = new ArrayList<>();
    choices.add(new LogChoice("Логи UPC-1", "log-samples\\bankplus.log"));
    choices.add(new LogChoice("Логи UPC-1", "log-samples\\logSample.log", true));
    choices.add(new LogChoice("Логи UPC-2", "log-samples/core.log"));
    choices.add(new LogChoice("Логи UPC-2", "log-samples/pppinfo.log"));
//    Thread.sleep(2000L);
    return choices;
  }
}