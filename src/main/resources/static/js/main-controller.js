app = angular.module("AnaLog", ['ngSanitize', 'ui.select']);

app.run(function ($rootScope) { $rootScope.watchingLog = "АнаЛог v0.7 (загрузка...)"; });

app.controller('controlPanelController', function ($scope, $rootScope,
                                                   choicesService, providerService, renderingService,
                                                   $location, $log, $interval) {
    var vm = this;

    var onAirPromise;
    var watching = null;
    $scope.encoding = 'utf8';
    $scope.onAir = false;
    $scope.prependingSize = "1";
    $scope.choices = [];

    vm.selectedLog = undefined;

    initChoicesAndLog();

    vm.onLogChange = function() {
        $log.log("New choice: " + vm.selectedLog.path + " (" + vm.selectedLog.encoding + ")");
        $scope.encoding = vm.selectedLog.encoding;
        $rootScope.watchingLog = vm.selectedLog.title + " - АнаЛог";
        $location.path(vm.selectedLog.path);
        $scope.onAir = false;
        renderingService.clearQueue();
        $scope.updateLog(/*readBackAllowed:*/true);
    };
    $scope.updateLog = function (isReadBackAllowed) {
        providerService(vm.selectedLog.path, $scope.encoding, $scope.stopOnAir, isReadBackAllowed)
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
        providerService(vm.selectedLog.path, $scope.encoding, $scope.stopOnAir, false, $scope.prependingSize);
    };
    $scope.clear = function () {
        renderingService.clearQueue();
    };
    $scope.stopOnAir = function () {
        $scope.onAir = false;
    };
    /**
     * Queries log choice options (choices) from server and initiates loading of selected log.
     */
    function initChoicesAndLog() {
        choicesService(function (choices, selectedChoice) {
            $scope.choices = choices;
            vm.selectedLog = selectedChoice;
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
        if (vm.selectedLog && !arePathsEqual(vm.selectedLog.path, newPath)) {
            $log.log("Path change detected from: '" + vm.selectedLog.path + "' to: '" + newPath +"'.");
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
            // onAirPromise = $interval(function() {$scope.updateLog(false)}, 1000);
            // $scope.updateLog(false);    // in order not to wait for the first interval triggering
            watching = {};
            watching.stompClient = Stomp.over(new SockJS('/watch-endpoint'));
            watching.stompClient.connect({}, function (frame) {
                $log.log('Connected: ' + frame);
                var callback = function (serverMessage) {
                    var newPart = JSON.parse(serverMessage.body);
                    var preparedLines = prepareMessages(newPart);
                    renderingService.appendMessages(preparedLines)
                };
                var subId, logId;
                if (vm.selectedLog.uid) {
                    logId = vm.selectedLog.uid;
                    subId = 'uid=' + logId;
                } else {
                    logId = vm.selectedLog.path;
                    subId = 'path=' + logId;
                }
                watching.subscription = watching.stompClient.subscribe('/topic/'+logId, callback, {id: subId});
            });

        } else {
            // $scope.disableOnAirPoller();
            if (watching !== null) {
                watching.subscription.unsubscribe();
                watching.stompClient.disconnect(function () {watching = null});
            }
        }
    });

});
