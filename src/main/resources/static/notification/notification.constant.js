/**
 * All the popup notifications that AnaLog is able to show.
 * Subject to localize.
 */
app.constant('notifications', {
    //<editor-fold desc="Server Events">
    serverConnected: {
        level: 'success',
        title: 'Server Is Available',
        text: 'Connection restored, we can go on working.'
    },
    serverDisconnected: {
        level: 'warning',
        title: 'No Connection With Server',
        text: 'Log tracking will continue (if necessary) after connection restore.'
    },
    //</editor-fold>

    //<editor-fold desc="Tail Events">
    logNotFound: {
        level: 'info',
        title: 'Log Not Found',
        text: "Log <span class='highlight'>{logPath}</span> not found. Waiting for it to appear..."
    },
    logAppeared: {
        level: 'success',
        title: 'Log Detected',
        text: "Log <span class='highlight'>{logPath}</span> has appeared. Following it..."
    },
    logRotated: {
        level: 'info',
        title: 'Log Rotation',
        text: "Log <span class='highlight'>{logPath}</span> started to write from scratch. " +
            "Perhaps previous records have been moved to another file."
    },
    logDisappeared: {
        level: 'info',
        title: 'Log Lost',
        text: "Log <span class='highlight'>{logPath}</span> has disappeared. The tracking will continue" +
            " automatically when the log is back."
    },
    logTruncated: {
        level: 'danger',
        title: 'Log Reduced',
        text: "Log <span class='highlight'>{logPath}</span> has become shorter.<br/>" +
                "Current tracking can become incorrect. Restart it if necessary."
    },
    unrecognized: {
        level: 'warning',
        title: 'Tracking Notification',
        text: "Log <span class='highlight'>{logPath}</span> produced a message:<br/>" +
                "<span class='tracking-message'>{message}</span>"
    },
    //</editor-fold>

    //<editor-fold desc="Server Fault(s)">
    serverFailure: {
        level: 'danger',
        title: 'Server Message',
        text: "Tracking has been stopped because of error:<br/><span class='failure-message'>{message}</span>"
    },
    choicesNotFound: {
        level: 'danger',
        title: 'Server Failure',
        text: "Couldn't fetch log choices because of error:<br/>" +
                "<span class='failure-message'>{message}</span>"
    }
    //</editor-fold>

});