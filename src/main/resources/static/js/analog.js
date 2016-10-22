var app = angular.module("AnaLog", []);
app.run(function ($rootScope) {
    $rootScope.watchingLog = "АнаЛог (загрузка...)";
});
app.controller("choicesController", function ($scope, $rootScope, $http) {
    $http.get("/choices")
        .then(function(response) {
            $scope.choices = response.data;
            for (var i in $scope.choices) {
                var choice = $scope.choices[i];
                if (choice.selectedByDefault) {
                    $scope.selectedChoice = choice;
                    $rootScope.watchingLog = choice.fileName + " - АнаЛог";
                    break;
                }
            }
            $scope.onLogChange = function () {
                console.log("New choice: " + $scope.selectedChoice.path)
                $rootScope.watchingLog = $scope.selectedChoice.fileName + " - АнаЛог";
            }
        });
});