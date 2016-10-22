<%--
  Created by IntelliJ IDEA.
  Date: 16.10.14
  Time: 16:46
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru">
<head>
  <title>АнаЛог v0.6</title>
  <meta http-equiv="content-type" content="text/html; charset=UTF-8"/>
  <script src="js/jquery-1.8.2.min.js" type="text/javascript"></script>
  <script src="js/highlight.pack.js" type="text/javascript"></script>
  <script src="js/main.js" type="text/javascript"></script>

  <link rel="stylesheet" href="css/highlight-js/github-gist.css">
  <link rel="stylesheet" href="css/main.css" type="text/css"/>
  <link rel="icon" type="image/png" href="img/favicon.png"/>
</head>
<body>
<script type="text/javascript">
  $(init);
</script>
<div id="controlPanel">
  <label>
    <input type="checkbox" name="updateToggler" onchange="setupTimer();"/>Обновлять ежесекундно&nbsp;
  </label>
  <button onclick="setNewLogFileName();" name="updateNowButton">Обновить сейчас</button>
  &nbsp;
  <label>
    <input type="checkbox" name="watchToggler" onchange="toggleWatching();"/>Следить
  </label>
  <label for="combobox">&nbsp;&nbsp;Файл:&nbsp;</label>
  <select id="combobox" name="logFileName" onchange="setNewLogFileName();">
    <%
      String localName = request.getServerName();
      String hostId = localName.substring(0, localName.indexOf("."));
      String pathToInclude = "log-options/" + hostId + ".jsp";
    %>
    <jsp:include page="<%= pathToInclude %>"/>
  </select>
  <label>
    <select id="encoding" name="encoding" onchange="setNewLogFileName();">
      <option value="cp1251">CP-1251</option>
      <option value="utf8">UTF-8</option>
    </select>
  </label>

  &nbsp;
  <div id="loader">&nbsp;</div>
  &nbsp;&nbsp;
  <button onclick="prependLog();" id="prependingMode">Промотать вверх</button>
  <label>
    <select name="prependingSize">
      <option value="1" selected="selected">на 1%</option>
      <option value="5">на 5%</option>
      <option value="10">на 10%</option>
      <option value="20">на 20%</option>
      <option value="30">на 30%</option>
      <option value="50">на 50%</option>
      <option value="100">до начала</option>
    </select>
  </label>

  &nbsp;&nbsp;
  <button onclick="$consolePanel.empty();">Очистить</button>
</div>
<br/>
<br/>

<div id="consolePanel"></div>
<img id="substrate" src="img/magnifier.jpg"/>
</body>
</html>
