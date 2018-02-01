# AnaL&oacute;g
#### :information_source: Note for English speakers
The tool is in trial stage now and is being tested by Russian speaking users. Therefore the tool's UI as well as its documentation are made in Russian only (for the time being). English translation is planned for the next development iteration. Please feel free to [file an issue](https://github.com/Toparvion/analog/issues/new) if you'd like it to happen sooner.

**АнаЛ&oacute;г** - это основанное на [tail](https://ru.wikipedia.org/wiki/Tail) веб-приложение, позволяющее в реальном времени просматривать одновременно несколько логов с удаленных тестовых серверов.  
Основные особенности программы:
* просмотр логов через веб браузер;  
_не требуется инсталляция специального ПО на клиентских машинах_;
* отслеживание логов в реальном времени;  
_не нужно перезагружать к себе просматриваемый файл или обновлять страницу;_
* подсветка уровней логирования;  
_сообщение каждого уровня (TRACE, DEBUG, INFO, WARN, ERROR, FATAL) выводится в своем собственном цвете;_
* форматирование XML;  
_АнаЛ&oacute;г распознает XML-документы, расставляет для них отступы (даже если изначально XML был однострочным) и подсвечивает их синтаксис;_
* поддержка композитных логов;  
_можно объединить несколько файлов (в т.ч. с разных серверов) в один виртуальный лог, который будет выводиться в браузере со строгим соблюдением временной последовательности и целостности многострочных записей;_
