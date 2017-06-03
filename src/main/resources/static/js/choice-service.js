/**
 * A service responsible for providing the application with log choice options (aka choices).
 */
function ChoicesService($http, $location, $log, $window) {
    return function (callbackWhenReady) {

        $http.get("/choices")
            .then(function success(response) {
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
                            $log.log("Proposed log is unknown and therefore will be added as separate group.");
                            selectedChoice = {
                                group: "Указан через URL",
                                title: extractFileName(proposedLogPath),
                                encoding: "UTF-8",
                                path: proposedLogPath,
                                uid: null   // to explicitly denote the absence of logConfigEntry on the server side
                            };
                            choices.push(selectedChoice);
                        }

                    } else {        // никакого лога в URL указано не было; полагаемся только на варианты от сервера
                        $log.log("No proposed log path was given in URL; basing on choices from server only.");
                        for (var j in choices) {
                            var choice = choices[j];
                            if (choice.selectedByDefault) {
                                selectedChoice = choice;
                                $location.path(choice.path);
                                break;
                            }
                        }
                    }

                    // сохраняем все результаты предшествующих выборов в модели
                    callbackWhenReady(choices, selectedChoice);
                },
                function fail(response) {
                    var message = 'Не удалось загрузить варианты логов с сервера: ' + response.status + ' ' + response.statusText;
                    if (response.data) {
                        message += '\n' + response.data.error + '\n' + response.data.message;
                    }
                    $window.alert(message);
                }

            );
    };
}

app.service('choicesService', ['$http', '$location', '$log', '$rootScope', '$window', ChoicesService]);