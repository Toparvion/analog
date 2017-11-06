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

// TODO move to Angular app initialization
// First, checks if it isn't implemented yet.
if (!String.prototype.format) {
    String.prototype.format = function() {
        var args = arguments;
        return this.replace(/{(\w+)}/g, function(match, varName) {
            return (typeof args[0][varName] !== 'undefined')
                ? args[0][varName]
                : match;
        });
    };
}
