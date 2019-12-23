function DownloadController($scope, $element, $attrs, $log, $http) {
    let ctrl = this;

    // behavioral (non-visual) state of controller
    ctrl.isShowingDialog = false;
    ctrl.isShowingButton = false;
    ctrl.lastError = undefined;
    ctrl.isLoading = false;
    ctrl.file2Download = undefined;

    // visual state of controller
    ctrl.currentSize = undefined;
    ctrl.lastModified = undefined;
    ctrl.node = undefined;
    ctrl.downloadLink = undefined;
    ctrl.allMembers = [];

    ctrl.toggleDialog = function () {
        ctrl.isShowingDialog = !ctrl.isShowingDialog;
        if (!ctrl.isShowingDialog)
            return;
        // $log.log("Going to request data for: %o", ctrl.selectedLog);
        ctrl.initDialogData();
    };

    ctrl.initDialogData = function() {
        if (!ctrl.selectedLog) {
            return [];
        }
        let path = extractPath(ctrl.selectedLog.id);
        let firstMember = {
            node: ctrl.selectedLog.node,
            path: path,
            file: extractFileName(path)
        };
        // NOTE: The following assignment will trigger fetchDataFromServer() via corresponding $watch!
        ctrl.file2Download = firstMember;
        ctrl.allMembers = new Array(firstMember);
/* TODO fix inclusion choice for composite logs
        if (ctrl.selectedLog.includes) {
            var otherMembers = ctrl.selectedLog.includes.map(function (member) {
                return {
                    node: member.node,
                    path: member.path,
                    file: extractFileName(member.path)
                }
            });
            ctrl.allMembers = ctrl.allMembers.concat(otherMembers);
        }
*/
    };

    ctrl.fetchDataFromServer = function () {
        let uriPath = '/download?path=' + ctrl.file2Download.path;
        // it doesn't matter whether specified node is remote or local; server will handle it itself
        if (angular.isDefined(ctrl.file2Download.node)) {
            uriPath += ("&node=" + ctrl.file2Download.node);
        }
        ctrl.isLoading = true;
        $http.head(uriPath)
            .then(
                // if OK
                function (response) {
                    ctrl.currentSize = response.headers('Content-Length');
                    ctrl.lastModified = response.headers('Last-Modified');
                    ctrl.lastError = undefined;
                    ctrl.downloadLink = uriPath;
                    ctrl.isLoading = false;
                },
                // if FAIL
                function (response) {
                    // $log.log("Failed to request data from server: %o", response);
                    ctrl.lastError = {
                        status: response.status,
                        message: response.statusText
                    };
                    ctrl.currentSize = undefined;
                    ctrl.lastModified = undefined;
                    ctrl.downloadLink = undefined;
                    ctrl.isLoading = false;
                })
    };

    ctrl.onDownloadClick = function () {
        if (ctrl.lastError || ctrl.isLoading) {
            return false;       // to prevent any actions of the link in case of error or in-progress loading

        } else {
            ctrl.closeDialog();
        }
        // don't return anything in order to let the caller proceed normally
    };

    ctrl.closeDialog = function () {
        ctrl.isShowingDialog = false;
    };

    // the following watch allows to react instantly on selected log changes when download dialog is open
    $scope.$watch(function () {
        return ctrl.selectedLog;
    }, function () {
        if (ctrl.isShowingDialog) {
            ctrl.initDialogData();
        }
        if (ctrl.selectedLog) {
            ctrl.isShowingButton = (ctrl.selectedLog.type === "LOCAL_FILE") || (ctrl.selectedLog.type === "NODE");
        }
    });

    // the following watch allows to react instantly on changes of selected file among composite log's file list
    $scope.$watch(function () {
        return ctrl.file2Download;
    }, function () {
        if (ctrl.isShowingDialog)
            ctrl.fetchDataFromServer();
    });
}

app.filter('sizeFormatter', function() {
    return function(bytes) {
        if (!bytes)
            return '[n/a]';
        if (bytes === 0)
            return '0 Bytes';
        let k = 1024,
            dm = /*decimals ||*/ 2,
            sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
            i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    };
});

app.filter('dateFormatter', function () {
   return function (utcDateString) {
       if (!utcDateString)
           return '[n/a]';
       let parsedDate = new Date(Date.parse(utcDateString));
       return parsedDate.toLocaleString("en-US");
   }
});

app.filter('errorFormatter', function () {
   return function (error) {
       if (!error)
           return 'OK';
       let text;
       switch (error.status) {
           case 404:
               text = 'not found';
               break;
           case 403:
               text = 'access denied';
               break;
           case 503:
               text = 'temporarily unavailable';
               break;
           default:
               text = 'can not be downloaded';
       }
       text += (' ' + '(HTTP ' + error.status);
       if (error.message)
           text += (' ' + error.message);
       text += ')';
       return text;
   }
});

app.component('downloadDialog', {
    templateUrl: 'download/download.template.html',
    controller: DownloadController,
    bindings: {
        selectedLog: '<'
    }
});