/**
 * All the popup notifications that AnaLog is able to show.
 * Subject to localize.
 */
app.constant('notifications', {
    //<editor-fold desc="Server Events">
    serverConnected: {
        level: 'success',
        title: 'Ура!',
        text: 'Связь с сервером восстановлена. Можно работать.'
    },
    serverDisconnected: {
        level: 'warning',
        title: 'Ой-ой...',
        text: 'Нет связи с сервером. Как восстановится, сообщу и продолжу отслеживание (если надо).'
    },
    //</editor-fold>

    //<editor-fold desc="Tail Events">
    fileNotFound: {
        level: 'info',
        title: 'Хм...',
        text: "Файл <span class='highlight'>{logPath}</span> не найден на узле " +
                "<span class='highlight'>{nodeName}</span>. Ожидаю его появления..."
    },
    fileAppeared: {
        level: 'success',
        title: 'Есть контакт!',
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
        title: 'Oops',
        text: "Файл <span class='highlight'>{logPath}</span> пропал с узла " +
                "<span class='highlight'>{nodeName}</span>. Продолжу отслеживание, когда появится."
    },
    fileTruncated: {
        level: 'danger',
        title: 'Ай-ай!',
        text: "Файл <span class='highlight'>{logPath}</span> на узле " +
                "<span class='highlight'>{nodeName}</span> сократился в размере.<br/>" +
                "Дальнейшее отслеживание может быть ошибочным.<br/>В этом случае лучше начать его заново."
    },
    //</editor-fold>

    //<editor-fold desc="Server Fault(s)">
    serverFailure: {
        level: 'danger',
        title: 'Ай-ай!',
        text: "На сервере произошел сбой; отслеживание прекращено.<br/>Для выяснения причин нужно поискать в логе" +
                " сервера вот такое сообщение:<br/><span class='failure-message'>{message}</span>"
    },
    choicesNotFound: {
        level: 'danger',
        title: 'Вот блин!',
        text: "Не удалось получить варианты логов с сервера из-за ошибки:<br/>" +
                "<span class='failure-message'>{message}</span>"
    }
    //</editor-fold>

});