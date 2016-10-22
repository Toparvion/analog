var app = angular.module("AnaLog", []);
app.run(function ($rootScope) {
    $rootScope.watchingLog = "АнаЛог (загрузка...)";
});

app.controller("choicesController", function ($scope, $rootScope, $http, $location, $log) {
    $http.get("/choices")
        .then(function(response) {
            var choices = response.data;
            var selectedChoice;

            // сначала проверим, был ли указан путь к логу в URL
            if (!angular.equals($location.search(), {}) && $location.search().log) {
                var proposedLogPath = $location.search().log;
                $log.log("Proposed log path is present in URL: " + proposedLogPath);
                // теперь попытаемся выяснить, есть ли указанный лог среди известных на сервера
                for (var i in choices) {
                    var knownChoice = choices[i];
                    if (knownChoice.path == proposedLogPath) {
                        $log.log("Proposed log is known in group: " + knownChoice.group);
                        selectedChoice = knownChoice;       // такой лог известен; просто выбираем его
                        break;
                    }
                }
                // если указанный лог неизвестен, создадим для него отдельный вариант выбора и добавим его в список
                if (!selectedChoice) {
                    $log.log("Proposed log is unknown and therefore will be added.");
                    selectedChoice = {
                        group: "Указан через URL",
                        fileName: proposedLogPath.substring(proposedLogPath.lastIndexOf("\\") + 1),
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
            $scope.choices = choices;
            $scope.selectedLog = selectedChoice;
            $rootScope.watchingLog = selectedChoice.fileName + " - АнаЛог";

        });

    $scope.onLogChange = function () {
        $log.log("New choice: " + $scope.selectedLog.path);
        $rootScope.watchingLog = $scope.selectedLog.fileName + " - АнаЛог";
        $location.search("log", $scope.selectedLog.path)
    }
});