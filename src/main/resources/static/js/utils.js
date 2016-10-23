function extractFileName(path) {
    var forwardSlashIndex = path.lastIndexOf('/');
    if (forwardSlashIndex != -1) {
        return path.substring(forwardSlashIndex + 1);
    }

    var backwardSlashIndex = path.lastIndexOf('\\');
    if (backwardSlashIndex != -1) {
        return path.substring(backwardSlashIndex + 1);
    }

    return path;
}