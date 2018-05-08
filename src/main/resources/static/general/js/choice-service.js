/**
 * A service responsible for providing the application with log choice options (aka choices).
 */
function ChoicesService($http, $location, $log, $rootScope) {
    return function () {

        var onSuccess = function success(response) {
            var choices = response.data;
            var selectedChoice = undefined;

            // сначала проверим, был ли указан путь к логу в URL
            if ($location.path()) {
                var proposedLogPath = $location.path();
                $log.log("Proposed log path found in URL: " + proposedLogPath);
                // теперь попытаемся выяснить, есть ли указанный лог среди известных на сервере
                for (var i in choices) {
                    var knownChoice = choices[i];
                    if (arePathsEqual(knownChoice.path, proposedLogPath)) {
                        $log.log("Proposed log is known within group: " + knownChoice.group);
                        selectedChoice = knownChoice;       // такой лог известен; просто выбираем его
                        break;
                    }
                }
                // если указанный лог неизвестен, создадим для него отдельный вариант выбора и добавим его в список
                if (!selectedChoice) {
                    $log.log("Proposed log is unknown among server choices and hence will be added as separate group.");
                    selectedChoice = {
                        group: "Указан через URL",
                        title: extractFileName(proposedLogPath),
                        path: proposedLogPath,
                        uid: null   // to explicitly denote the absence of logConfigEntry on the server side
                    };
                    choices.push(selectedChoice);
                }

            } else {        // никакого лога в URL указано не было; полагаемся только на варианты от сервера
                $log.log("No proposed log path was given in URL; basing on choices from server only.");
                for (var j in choices) {
                    var choice = choices[j];
                    if (choice.selected) {
                        selectedChoice = choice;
                        $location.path(choice.path);
                        break;
                    }
                }
            }

            // сохраняем все результаты предшествующих выборов в модели
            $rootScope.$broadcast('choicesReady', {choices: choices, selectedChoice: selectedChoice});
        };

        var onFail = function fail(response) {
            var message = '';
            if (response.status) {
                message += ('HTTP ' + response.status);
            }
            if (response.statusText) {
                message += (' (' + response.statusText + ')');
            }
            if (response.data) {
                if (response.data.error) {
                    if (message) message += ': ';
                    message += (response.data.error);
                }
                if (response.data.message) {
                    if (message) message += ' - ';
                    message += (response.data.message);
                }
            }
            $log.log("Failed to fetch choices from server. Broadcasting failure message: '" + message + "'");
            $rootScope.$broadcast('choicesNotFound', {message: message});
        };

        $http.get("/choices")
             .then(onSuccess, onFail);
    };
}

app.service('choicesService', ['$http', '$location', '$log', '$rootScope', ChoicesService]);