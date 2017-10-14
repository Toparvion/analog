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
