/**
 * A service responsible for providing the application with log choice options (aka choices).
 */
function ChoicesService($http, $location, $log, $rootScope) {
    return function () {

        let onSuccess = function success(response) {
            let choices = response.data;
            let selectedChoice = null;

            // сначала проверим, был ли указан путь к логу в URL
            if ($location.path()) {
                let proposedLogId = removeSlashIfNeeded($location.path());
                $log.log("Proposed log ID found in URL: " + proposedLogId);
                // теперь попытаемся выяснить, есть ли указанный лог среди известных на сервере
                for (let i in choices) {
                    let knownChoice = choices[i];
                    if (arePathsEqual(knownChoice.id, proposedLogId)) {
                        $log.log("Proposed log is known within group: " + knownChoice.group);
                        selectedChoice = knownChoice;       // такой лог известен; просто выбираем его
                        break;
                    }
                }
                // если указанный лог неизвестен, создадим для него отдельный вариант выбора и добавим его в список
                if (!selectedChoice) {
                    $log.log("Proposed log is unknown among server choices and hence will be added as separate group.");
                    let logType = detectLogType(proposedLogId);
                    selectedChoice = {
                        group: "Указан через URL",
                        title: extractFileName(proposedLogId),
                        type: logType,
                        id: proposedLogId
                    };
                    if (logType === "NODE") {
                        selectedChoice.node = extractNode(proposedLogId);
                    }
                    choices.push(selectedChoice);
                }

            } else {        // никакого лога в URL указано не было; полагаемся только на варианты от сервера
                $log.log("No proposed log path was given in URL; basing on choices from server only.");
                for (let j in choices) {
                    let choice = choices[j];
                    if (choice.selected) {
                        selectedChoice = choice;
                        $location.path(addSlashIfNeeded(choice.id));
                        break;
                    }
                }
            }

            // сохраняем все результаты предшествующих выборов в модели
            $rootScope.$broadcast('choicesReady', {choices: choices, selectedChoice: selectedChoice});
        };

        let onFail = function fail(response) {
            let message = '';
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