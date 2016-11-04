/**
 * A service responsible for providing the application with log choice options (choices).
 *
 * @param $http
 * @param $location
 * @param $log
 * @param $rootScope
 * @param $window
 * @returns {Function}
 * @constructor
 */
function ChoicesService($http, $location, $log, $rootScope, $window) {
    return function (callbackWhenReady) {

        $http.get("/choices")
            .then(function success(response) {
                    var choices = response.data;
                    var selectedChoice = undefined;

                    // сначала проверим, был ли указан путь к логу в URL
                    if (!angular.equals($location.search(), {}) && $location.search().log) {
                        var proposedLogPath = $location.search().log;
                        $log.log("Found proposed log path in URL: " + proposedLogPath);
                        // теперь попытаемся выяснить, есть ли указанный лог среди известных на сервере
                        for (var i in choices) {
                            var knownChoice = choices[i];
                            // next replacements allow us to correctly compare paths from different OS
                            if (knownChoice.path.replace(new RegExp("\\\\",'g'), "/")
                                == proposedLogPath.replace(new RegExp("\\\\", 'g'), "/")) {
                                $log.log("Proposed log is known in group: " + knownChoice.group);
                                selectedChoice = knownChoice;       // такой лог известен; просто выбираем его
                                break;
                            }
                        }
                        // если указанный лог неизвестен, создадим для него отдельный вариант выбора и добавим его в список
                        if (!selectedChoice) {
                            $log.log("Proposed log is unknown and therefore will be added as separate group.");
                            selectedChoice = {
                                group: "Указан через URL",
                                fileName: extractFileName(proposedLogPath),
                                path: proposedLogPath
                            };
                            choices.push(selectedChoice);
                        }

                    } else {        // никакого лога в URL указано не было; полагаемся только на варианты от сервера
                        $log.log("No proposed log path was given in URL; basing on choices from server only.");
                        for (var j in choices) {
                            var choice = choices[j];
                            if (choice.selectedByDefault) {
                                selectedChoice = choice;
                                $location.search("log", choice.path);
                                break;
                            }
                        }
                    }

                    // сохраняем все результаты предшествующих выборов в модели
                    callbackWhenReady(choices, selectedChoice);
                },
                function fail(response) {
                    $window.alert('Не удалось загрузить варианты логов с сервера: '
                        + response.status + ' ' + response.statusText
                        + '\n' + response.data.error + '\n' + response.data.message);
                }

                );
    };
}

app.service('choicesService', ['$http', '$location', '$log', '$rootScope', '$window', ChoicesService]);