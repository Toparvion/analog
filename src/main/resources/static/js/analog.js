app = angular.module("AnaLog", []);

app.run(function ($rootScope) { $rootScope.watchingLog = "АнаЛог v0.7 (загрузка...)"; });

app.controller("choicesController", function ($scope, $rootScope, choicesService, $location, $log) {

    choicesService($scope);

    $scope.onLogChange = function () {
        $log.log("New choice: " + $scope.selectedLog.path);
        $rootScope.watchingLog = $scope.selectedLog.fileName + " - АнаЛог";
        $location.search("log", $scope.selectedLog.path)
    }
});

