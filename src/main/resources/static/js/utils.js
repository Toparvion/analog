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

function prepareMessages(responseData) {
    var preparedMessages = [];
    angular.forEach(responseData.items, function (item) {
        var $messageLine;
        if (item.level !== 'XML') {
            $messageLine = $('<div class="' + item.level + '">' + item.text + '</div>');
        } else {
            $messageLine = $('<pre class="xml"><code>' + item.text + '</code></pre>');
            hljs.highlightBlock($messageLine[0]);
        }
        preparedMessages.push($messageLine);
    });
    return preparedMessages;
}

function initHashCodeSupport() {
    String.prototype.hashCode = function(){
        var hash = 0;
        if (this.length == 0) return hash;
        for (i = 0; i < this.length; i++) {
            char = this.charCodeAt(i);
            hash = ((hash<<5)-hash)+char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return hash;
    }
}