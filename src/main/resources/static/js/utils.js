function extractFileName(path) {
    var lastSlashPosition = Math.max(
        path.lastIndexOf('/'),
        path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
}

function arePathsEqual(path1, path2) {
    // the following replacements allow us to correctly compare paths from different OS'es
    return (path1.toLowerCase().replace(new RegExp("\\\\", 'g'), "/") === path2.toLowerCase().replace(new RegExp("\\\\", 'g'), "/"));
}

function preparePlainMessages(newPart) {
    console.log("Preparing PLAIN messages: ", newPart);
    var preparedMessages = [];
    var $partLines = $("<div></div>").hide();
    angular.forEach(newPart.lines, function (line) {
        var $messageLine;
        if (line.style !== 'XML') {
            $messageLine = $("<div></div>")
                .addClass(line.style)
                .html(line.text);
        } else {
            var $code = $("<code></code>")
                .addClass("xml")
                .html(line.text);
            $messageLine = $("<pre></pre>").append($code);
            hljs.highlightBlock($messageLine[0]);
        }
        $partLines.append($messageLine);
    });
    $("#consolePanel").append($partLines);
    $partLines.slideDown();         // TODO animate like composite ones (including scroll down button)
    return preparedMessages;
}
