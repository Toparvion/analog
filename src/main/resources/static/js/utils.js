function extractFileName(path) {
    var lastSlashPosition = Math.max(
        path.lastIndexOf('/'),
        path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
}

function prepareMessages(responseData) {
    var preparedMessages = [];
    angular.forEach(responseData.items, function (item) {
        var $messageLine;
        if (item.level != 'XML') {
            $messageLine = $('<div class="' + item.level + '">' + item.text + '</div>');
        } else {
            $messageLine = $('<pre class="xml"><code>' + item.text + '</code></pre>');
            hljs.highlightBlock($messageLine[0]);
        }
        preparedMessages.push($messageLine);
    });
    return preparedMessages;
}