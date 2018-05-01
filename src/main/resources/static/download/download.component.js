function DownloadController($scope, $element, $attrs, $log, $http) {
    var ctrl = this;

    // behavioral (non-visual) state of controller
    ctrl.isShowingDialog = false;
    ctrl.lastError = undefined;
    ctrl.isLoading = false;

    // visual state of controller
    ctrl.currentSize = undefined;
    ctrl.lastModified = undefined;
    ctrl.node = undefined;
    ctrl.downloadLink = undefined;

    ctrl.toggleDialog = function () {
        ctrl.isShowingDialog = !ctrl.isShowingDialog;
        if (!ctrl.isShowingDialog)
            return;

        // $log.log("Going to request data for: %o", ctrl.selectedLog);
        ctrl.loadData();
    };

    ctrl.loadData = function () {
        var uriPath = '/download?path=' + ctrl.selectedLog.path;
        if (ctrl.selectedLog.nodes.length) {
            uriPath += ("&node=" + ctrl.selectedLog.nodes[0]);
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
        if (ctrl.isShowingDialog)
            ctrl.loadData();
    });
}

app.filter('sizeFormatter', function() {
    return function(bytes) {
        if (!bytes)
            return '[н/д]';
        if (bytes === 0)
            return '0 Байт';
        var k = 1024,
            dm = /*decimals ||*/ 2,
            sizes = ['Байт', 'КБ', 'МБ', 'ГБ', 'ТБ', 'ПБ', 'ЭБ', 'ЗБ', 'YB'],
            i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    };
});

app.filter('dateFormatter', function () {
   return function (utcDateString) {
       if (!utcDateString)
           return '[н/д]';
       var parsedDate = new Date(Date.parse(utcDateString));
       return parsedDate.toLocaleString("ru-RU");
   }
});

app.filter('errorFormatter', function () {
   return function (error) {
       if (!error)
           return 'OK';
       var text;
       switch (error.status) {
           case 404:
               text = 'не найден';
               break;
           case 503:
               text = 'временно не доступен';
               break;
           default:
               text = 'не может быть получен';
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