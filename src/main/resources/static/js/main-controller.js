app = angular.module("AnaLog", []);

app.run(function ($rootScope) { $rootScope.watchingLog = "АнаЛог v0.6 (загрузка...)"; });

app.controller('controlPanelController', function ($scope, $rootScope,
                                                   choicesService, providerService, renderingService,
                                                   $location, $log, $interval) {
    var onAirPromise;
    $scope.encoding = 'utf8';
    $scope.onAir = false;
    $scope.prependingSize = "1";

    initChoicesAndLog();

    $scope.onLogChange = function () {
        $log.log("New choice: " + $scope.selectedLog.path + " (" + $scope.selectedLog.encoding + ")");
        $scope.encoding = $scope.selectedLog.encoding;
        $rootScope.watchingLog = $scope.selectedLog.title + " - АнаЛог";
        $location.path($scope.selectedLog.path);
        $scope.onAir = false;
        renderingService.clearQueue();
        $scope.updateLog(/*readBackAllowed:*/true);
    };
    $scope.updateLog = function (isReadBackAllowed) {
        providerService($scope.selectedLog.path, $scope.encoding, $scope.stopOnAir, isReadBackAllowed)
    };
    $scope.disableOnAirPoller = function() {
        if (angular.isDefined(onAirPromise)) {
            $interval.cancel(onAirPromise);
            onAirPromise = undefined;
            $log.log("Auto updating timer has been stopped.");
        }
    };
    $scope.$on('$destroy', function() {
        $scope.disableOnAirPoller();
    });
    $scope.prepend = function () {
        $scope.onAir = false;
        providerService($scope.selectedLog.path, $scope.encoding, $scope.stopOnAir, false, $scope.prependingSize);
    };
    $scope.clear = function () {
        renderingService.clearQueue();
    };
    $scope.stopOnAir = function () {
        $scope.onAir = false;
    };
    /**
     * Queries log choice options (choices) from backend and initiates loading of selected log.
     */
    function initChoicesAndLog() {
        choicesService(function (choices, selectedChoice) {
            $scope.choices = choices;
            $scope.selectedLog = selectedChoice;
            $scope.encoding = selectedChoice.encoding;
            $rootScope.watchingLog = selectedChoice.title + " - АнаЛог";
            $scope.updateLog(/*readBackAllowed:*/true);
        });
    }
    // the following watch allows us to react to URL path change instantly (without opening a new browser tab)
    $scope.$watch(function () {
        return $location.path();
    }, function (value) {
        // $log.log("Raw value: " + value); // helpful for troubleshooting paths starting with 'C:\'
        var newPath = value;
        if ($scope.selectedLog && !arePathsEqual($scope.selectedLog.path, newPath)) {
            $log.log("Path change detected from: '" + $scope.selectedLog.path + "' to: '" + newPath +"'.");
            $scope.onAir = false;
            $scope.clear();
            initChoicesAndLog();
        }
    });

    // the following watch allows us to react to any change of onAir mode flag (both from UI or internally)
    $scope.$watch(function () {
        return $scope.onAir;
    }, function () {
        $log.log("Turning onAir to: " + $scope.onAir);
        if ($scope.onAir) {
            onAirPromise = $interval(function() {$scope.updateLog(false)}, 1000);
            $scope.updateLog(false);    // in order not to wait for the first interval triggering
        } else {
            $scope.disableOnAirPoller();
        }
    });

});
