<%@ page import="org.apache.log4j.Logger" %>
<%@ page import="ru.ftc.upc.testing.analog.AnaLogUtils" %>
<%@ page import="ru.ftc.upc.testing.analog.ReadingMetaData" %>
<%@ page import="java.io.FileNotFoundException" %>
<%@ page import="java.util.List" %>
<%@ page import="static ru.ftc.upc.testing.analog.AnaLogUtils.nvls" %>
<%------------------------------------------ вспомогательная секция -------------------------------------------------%>
<%!
  Logger logger = Logger.getLogger(getClass());
%>

<%------------------------------------------ исполнительная секция --------------------------------------------------%>
<%
  // исключаем прямое (без параметров) обращение
  if (!request.getParameterMap().containsKey("callback")) {
    response.sendRedirect("/");
    return;
  }
  // определяем имя файла для чтения
  String inputFileName = request.getParameterMap().containsKey("log")
          ? request.getParameter("log")
          : "/pub/home/upc/applications/upcManualTesting/log/bankplus.log";
  // получаем данные о предыдущем чтении
  ReadingMetaData readingMetaData = AnaLogUtils.retrieveMetaData(session, inputFileName);

  // получаем сырой набор строк из файла
  List<String> rawLines;
  try {
    Long prependingSnippetSizePercent = request.getParameterMap().containsKey("prependingSnippetSizePercent")
            ? Long.valueOf(request.getParameter("prependingSnippetSizePercent"))
            : null;
    String encoding = nvls(request.getParameter("encoding"), "utf8");
    rawLines = AnaLogUtils.getRawLines(inputFileName, encoding, readingMetaData, prependingSnippetSizePercent);

  } catch (FileNotFoundException e) {
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    out.print("Ошибка при чтении заданного файла: " + e.getMessage());
    return;

  } catch (Exception e) {
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    logger.error("Internal application error: ", e);
    out.print("Внутренняя ошибка утилиты: " + e.getMessage());
    return;
  }
  // настройки формирования ответа
  response.setContentType("application/json;charset=UTF-8");
  StringBuilder sb = new StringBuilder();
  sb.append(request.getParameter("callback")).append("(");
  AnaLogUtils.writeResponse(sb, rawLines);
  sb.append(")");
  out.write(sb.toString());
%>
