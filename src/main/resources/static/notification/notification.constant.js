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
    fileNotFound: {
        level: 'info',
        title: 'Лог не найден',
        text: "Файл <span class='highlight'>{logPath}</span> не найден на узле " +
                "<span class='highlight'>{nodeName}</span>. Ожидаю его появления..."
    },
    fileAppeared: {
        level: 'success',
        title: 'Лог обнаружен',
        text: "Файл <span class='highlight'>{logPath}</span> на узле <span class='highlight'>{nodeName}</span> " +
                "появился. Отслеживаю его изменения."
    },
    fileRotated: {
        level: 'info',
        title: 'Ротация лога',
        text: "Файл <span class='highlight'>{logPath}</span> на узле <span class='highlight'>{nodeName}</span> " +
                "начал писаться с начала. Предыдущие записи, вероятно, перенесены в другой файл."
    },
    fileDisappeared: {
        level: 'info',
        title: 'Лог потерян',
        text: "Файл <span class='highlight'>{logPath}</span> пропал с узла " +
                "<span class='highlight'>{nodeName}</span>. Продолжу отслеживание, когда появится."
    },
    fileTruncated: {
        level: 'danger',
        title: 'Лог сократился',
        text: "Файл <span class='highlight'>{logPath}</span> на узле " +
                "<span class='highlight'>{nodeName}</span> сократился в размере.<br/>" +
                "Дальнейшее отслеживание может быть ошибочным.<br/>В этом случае лучше начать его заново."
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