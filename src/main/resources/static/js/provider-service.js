function ProviderService($http, $window, renderingService) {
    return function (logPath, encoding, errorCallback, isReadBackAllowed, prependingSize) {
        var params = {
            log: logPath,
            encoding: encoding,
            readBackAllowed: isReadBackAllowed
        };
        if (prependingSize) {
            params.prependingSize = prependingSize;
        }
        $http.get("/provide", {params: params})
            .then(function success(response) {
                    var preparedMessages = preparePlainMessages(response.data);
                    if (!prependingSize) {
                        renderingService.appendMessages(preparedMessages);
                    } else {
                        renderingService.prependMessages(preparedMessages);
                    }
                },
                function fail(response) {
                    errorCallback();
                    var message = 'Не удалось загрузить лог с сервера: ' + response.status + ' ' + response.statusText;
                    if (response.data) {
                        message += '\n' + response.data.error + '\n' + response.data.message;
                    }
                    $window.alert(message);
                });
    }
}

app.service('providerService', ['$http', '$window', 'renderingService', ProviderService]);
