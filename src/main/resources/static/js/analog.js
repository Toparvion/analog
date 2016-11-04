app = angular.module("AnaLog", []);

app.run(function ($rootScope) { $rootScope.watchingLog = "АнаЛог v0.7 (загрузка...)"; });

app.controller('controlPanelController', function ($scope, $rootScope,
                                                   choicesService, providerService, renderingService,
                                                   $location, $log, $interval) {
    var onAirPromise;

    choicesService(function (choices, selectedChoice) {
        $scope.choices = choices;
        $scope.selectedLog = selectedChoice;
        $rootScope.watchingLog = selectedChoice.fileName + " - АнаЛог";
        $scope.updateNow();
    });
    $scope.encoding = 'utf8';
    $scope.onAir = false;
    $scope.prependingSize = "1";
    // $scope.$watch('onAir', $scope.toggleOnAir);
    $scope.onLogChange = function () {
        $log.log("New choice: " + $scope.selectedLog.path);
        $rootScope.watchingLog = $scope.selectedLog.fileName + " - АнаЛог";
        $location.search("log", $scope.selectedLog.path);
        renderingService.clearQueue();
        $scope.onAir = false;
        $scope.toggleOnAir();
        $scope.updateNow($scope.selectedLog);
    };
    $scope.updateNow = function () {
        providerService($scope.selectedLog.path, $scope.encoding)
    };
    $scope.toggleOnAir = function () {
        $log.log("Turning onAir to: " + $scope.onAir);
        if ($scope.onAir) {
            onAirPromise = $interval($scope.updateNow, 1000);
            $scope.updateNow();    // in order not to wait for the first interval triggering
        } else {
            $scope.stopOnAir();
        }
    };
    $scope.stopOnAir = function() {
        if (angular.isDefined(onAirPromise)) {
            $interval.cancel(onAirPromise);
            onAirPromise = undefined;
            $log.log("Auto updating timer has been stopped.");
        }
    };
    $scope.$on('$destroy', function() {
        $scope.stopOnAir();
    });
    $scope.prepend = function () {
        if ($scope.onAir == true) {
            $scope.onAir = false;
            $scope.toggleOnAir();
        }
        providerService($scope.selectedLog.path, $scope.encoding, $scope.prependingSize);
    };
    $scope.clear = function () {
        renderingService.clearQueue();
    }
});
