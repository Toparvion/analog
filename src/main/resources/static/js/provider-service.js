function ProviderService($http, $window, renderingService) {
    return function (logPath, encoding, prependingSize) {
        var params = {
            log: logPath,
            encoding: encoding
        };
        if (prependingSize) {
            params.prependingSize = prependingSize;
        }
        $http.get("/provide", {params: params})
            .then(function success(response) {
                    var preparedMessages = prepareMessages(response.data);
                    if (!prependingSize) {
                        renderingService.appendMessages(preparedMessages);
                    } else {
                        renderingService.prependMessages(preparedMessages);
                    }
                },
                function fail(response) {
                    $window.alert('Не удалось загрузить лог с сервера: '
                        + response.status + ' ' + response.statusText
                        + '\n' + response.data.error + '\n' + response.data.message);
                });
    }
}

app.service('providerService', ['$http', '$window', 'renderingService', ProviderService]);
