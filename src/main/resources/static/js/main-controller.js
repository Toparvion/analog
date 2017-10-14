app = angular.module("AnaLog", ['ngSanitize', 'ui.select']);

app.run(function ($rootScope) {
    $rootScope.watchingLog = "АнаЛог v0.7 (загрузка...)";
});

app.controller('mainController', function ($scope, $rootScope, $window,
                                           choicesService, renderingService,
                                           $location, $log) {
    var vm = this;
    vm.watching = undefined;
    vm.selectedLog = undefined;
    vm.onAir = false;
    vm.textWrap = true;

    $scope.choices = [];

    /**
     * Queries log choice options (choices) from server and initiates loading of selected log.
     */
    vm.initChoicesAndLog = function () {
        choicesService(function (choices, selectedChoice) {
            $scope.choices = choices;
            vm.selectedLog = selectedChoice;
            $rootScope.watchingLog = selectedChoice.title + " - АнаЛог";
        });
    };
    vm.initChoicesAndLog();         // in order to initialize console panel at the time of loading

    vm.onLogChange = function() {
        $log.log("New choice: " + vm.selectedLog.path);
        $rootScope.watchingLog = vm.selectedLog.title + " - АнаЛ&oacute;г";
        $location.path(vm.selectedLog.path);
        vm.onAir = false;
        renderingService.clearQueue();
    };
    $scope.$on('$destroy', function() {
        vm.onAir = false;
    });
    vm.clear = function () {
        renderingService.clearQueue();
    };
    vm.scrollDown = renderingService.scrollDown;
    // the following watch allows us to react to URL path change instantly (without opening a new browser tab)
    $scope.$watch(function () {
        return $location.path();
    }, function (value) {
        // $log.log("Raw value: " + value); // helpful for troubleshooting paths starting with 'C:\'
        var newPath = value;
        if (vm.selectedLog && !arePathsEqual(vm.selectedLog.path, newPath)) {
            $log.log("Path change detected from: '" + vm.selectedLog.path + "' to: '" + newPath +"'.");
            vm.onAir = false;
            vm.clear();
            vm.initChoicesAndLog();
        }
    });
    // the following watch allows us to react to any change of onAir mode flag (both from UI and internally)
    $scope.$watch(function () {
        return vm.onAir;
    }, function () {
        $log.log("Turning onAir to: " + vm.onAir);
        if (vm.onAir) {
            // TODO extract to separate Angular service
            vm.watching = {};
            vm.watching.stompClient = Stomp.over(function () {return new SockJS('/watch-endpoint');});
            vm.watching.stompClient.connect({}, function (frame) {
                $log.log('Connected: ' + frame);
                var callback = function (serverMessage) {
                    var newPart = JSON.parse(serverMessage.body);
                    if (newPart.timestamp) {
                        renderingService.renderCompositeMessages(newPart)
                    } else {
                        renderingService.renderPlainMessages(newPart);
                    }
                };
                var subId, logId;
                if (vm.selectedLog.uid) {
                    logId = vm.selectedLog.uid;
                    subId = 'uid=' + logId;
                } else {
                    logId = vm.selectedLog.path;
                    subId = 'path=' + logId;
                }
                vm.watching.subscription = vm.watching.stompClient.subscribe('/topic/'+logId, callback, {id: subId});
            });
        } else {
            if (angular.isDefined(vm.watching)) {
                vm.watching.subscription.unsubscribe();
                vm.watching.stompClient.disconnect(function () {
                    vm.watching = undefined
                });
                $log.log("Watching has been turned off.");
            }
        }
    });

});
