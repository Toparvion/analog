/**
 * All the popup notifications that AnaLog is able to show.
 * Subject to localize.
 */
app.constant('notifications', {
    //<editor-fold desc="Server Events">
    serverConnected: {
        level: 'success',
        title: 'Сервер снова доступен',
        text: 'Связь восстановлена, можно работать.'
    },
    serverDisconnected: {
        level: 'warning',
        title: 'Нет связи с сервером',
        text: 'Слежение продолжится автоматически (если нужно) после восстановления связи.'
    },
    //</editor-fold>

    //<editor-fold desc="Tail Events">
    logNotFound: {
        level: 'info',
        title: 'Лог не найден',
        text: "Лог <span class='highlight'>{logPath}</span> не найден. Ожидаю его появления..."
    },
    logAppeared: {
        level: 'success',
        title: 'Лог обнаружен',
        text: "Лог <span class='highlight'>{logPath}</span> появился. Отслеживаю его изменения."
    },
    logRotated: {
        level: 'info',
        title: 'Ротация лога',
        text: "Лог <span class='highlight'>{logPath}</span> начал писаться с начала. " +
            "Предыдущие записи, вероятно, перенесены в другой лог."
    },
    logDisappeared: {
        level: 'info',
        title: 'Лог потерян',
        text: "Лог <span class='highlight'>{logPath}</span> пропал. Продолжу отслеживание, когда появится."
    },
    logTruncated: {
        level: 'danger',
        title: 'Лог сократился',
        text: "Лог <span class='highlight'>{logPath}</span> сократился в размере.<br/>" +
                "Дальнейшее отслеживание может быть ошибочным.<br/>В этом случае лучше начать его заново."
    },
    unrecognized: {
        level: 'warning',
        title: 'Сообщение о слежении',
        text: "При слежении за логом <span class='highlight'>{logPath}</span> получено сообщение:<br/>" +
                "<span class='failure-message'>{message}</span>"
    },
    //</editor-fold>

    //<editor-fold desc="Server Fault(s)">
    serverFailure: {
        level: 'danger',
        title: 'Сбой на сервере',
        text: "Отслеживание прекращено из-за ошибки:<br/><span class='failure-message'>{message}</span>"
    },
    choicesNotFound: {
        level: 'danger',
        title: 'Сбой на сервере',
        text: "Не удалось получить варианты логов из-за ошибки:<br/>" +
                "<span class='failure-message'>{message}</span>"
    }
    //</editor-fold>

});