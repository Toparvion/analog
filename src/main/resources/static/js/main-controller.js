app = angular.module("AnaLog", ['ngSanitize', 'ngAnimate', 'ui.select']);

app.run(function ($rootScope) {
    $rootScope.watchingLog = "АнаЛ&oacute;г v0.7 (загрузка...)";
});

app.controller('mainController', function ($scope, $rootScope, $window,
                                           choicesService, renderingService, watchingService,
                                           $location, $log) {
    var vm = this;
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
            $rootScope.watchingLog = selectedChoice.title + " - АнаЛ&oacute;г";
            watchingService.connect();
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
            watchingService.startWatching(vm.selectedLog)
        } else {
            watchingService.stopWatching();
        }
    });
    // to stop watching in case the server gets disconnected
    $scope.$on('serverDisconnected', function () {
        vm.onAir = false;
    });
    // to stop watching in case of server failure
    $scope.$on('serverFailure', function () {
        vm.onAir = false;
    });
    // to explicitly stop watching and close server connection upon termination
    $scope.$on('$destroy', function() {
        vm.onAir = false;
        watchingService.disconnect();
    });

});
