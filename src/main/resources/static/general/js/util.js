/**
 * @param path - any log path (including custom, e.g. k8s://deploy/some-pod}
 * @returns {string} the part of the path after last forward or backward slash; in case of custom path it will not
 * be the file name but the target resource name (e.g. container for Docker or pod for Kubernetes)
 */
function extractFileName(path) {
    let lastSlashPosition = Math.max(
        path.lastIndexOf('/'),
        path.lastIndexOf('\\'));
    return path.substring(lastSlashPosition + 1);
}

function removeSlashIfNeeded(logId) {
    const leadingSlashRegExp = /^\//;
    if (logId.includes(":")) {          // it means any kind of path expect pure Unix path like '/home/user/path'
        logId = logId.replace(leadingSlashRegExp, "");
        // console.log('Removed leading slash: %s', logId);
    }
    return logId;
}

function addSlashIfNeeded(logId) {
    if (logId.includes(":") && logId.indexOf('/') !== 0) {
        logId = '/' + logId;
        // console.log('Added leading slash: %s', logId);
    }
    return logId;
}

function arePathsEqual(path1, path2) {
    // the following replacements allow us to correctly compare paths from different OS'es
    let normalizedPath1 = path1.toLowerCase().replace(new RegExp("\\\\", 'g'), "/");
    let normalizedPath2 = path2.toLowerCase().replace(new RegExp("\\\\", 'g'), "/");
    return (normalizedPath1 === normalizedPath2);
}

function quantify(count) {
    if (count > 1) {
        return 'logs';
    } else {
        return 'log';
    }
}

function detectLogType(logId) {
    let tokens = logId.split("://");
    if (tokens.length > 1) {
        return tokens[0].toUpperCase();
    } else {
        return "LOCAL_FILE";
    }
}

function extractNode(logId) {
    let matchResult = logId.match(/^node:\/\/(\w+).*/i);
    if (matchResult) {
        return matchResult[1];
    } else {
        return "(n/a)";
    }
}

function extractPath(logId) {
    let matchResult = logId.match(/^node:\/\/\w+(.*)/i);
    if (!matchResult) {
        return logId;
    } else {
        return removeSlashIfNeeded(matchResult[1]);
    }
}

// TODO move to Angular app initialization
// First, checks if it isn't implemented yet.
if (!String.prototype.format) {
    String.prototype.format = function() {
        let args = arguments;
        return this.replace(/{(\w+)}/g, function(match, varName) {
            return (typeof args[0][varName] !== 'undefined')
                ? args[0][varName]
                : match;
        });
    };
}
