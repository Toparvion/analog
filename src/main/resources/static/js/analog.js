angular.module("AnaLog", [])
.controller("choicesControler", function ($scope, $http) {
    $http.get("/choices")
        .then(function(response) {
            console.log(response.data);
            $scope.choices = response.data;
            angular.forEach($scope.choices, function (value) {
                if (value.selectedByDefault) {
                    $scope.selectedChoice = value.path;
                }
            });
            // $scope.myWelcome = response.data;
            $scope.logChoice = function () {
                console.log("New choice: " + $scope.selectedChoice)
            }
        });
});