app = angular.module("AnaLog", ['ngSanitize', 'ngAnimate', 'ui.select']);

app.run(function ($rootScope, watchingService) {
    $rootScope.watchingLog = "АнаЛ&oacute;г v0.8 (загрузка...)";
    watchingService.connect();
});

app.controller('mainController', function ($scope, $rootScope, $window,
                                           choicesService, renderingService, watchingService, config,
                                           $location, $log) {
    var vm = this;
    vm.selectedLog = undefined;
    vm.onAir = false;
    vm.textWrap = true;
    vm.launching = true;            // one-time trigger to automate the very first activating of log watching

    $scope.choices = [];

    vm.onLogChange = function() {
        $log.log("New choice: " + vm.selectedLog.path);
        $rootScope.watchingLog = vm.selectedLog.title + " - " + config.general.appTitle;
        $location.path(vm.selectedLog.path);
        vm.clear();
        if (vm.onAir) {
            watchingService.stopWatching();
            watchingService.startWatching(vm.selectedLog, true)
        }
    };
    // a couple of bindings between this controller's functions and injected services
    vm.clear = renderingService.clearConsole;
    vm.scrollDown = renderingService.scrollDown;

    // the following watch allows us to react to URL path change instantly (without opening a new browser tab)
    $scope.$watch(function () {
        return $location.path();
    }, function (value) {
        // $log.log("Raw value: " + value); // helpful for troubleshooting paths starting with 'C:\'
        var newPath = value;
        if (vm.selectedLog && !arePathsEqual(vm.selectedLog.path, newPath)) {
            $log.log("Path change detected from: '" + vm.selectedLog.path + "' to: '" + newPath +"'.");
            vm.clear();
            choicesService();
            // then the watching will be reactivated (if needed) during the handling of choicesReady event
        }
    });
    // the following watch allows us to react to any change of onAir mode flag (both from UI and internally)
    $scope.$watch(function () {
        return vm.onAir;
    }, function () {
        var needTail = renderingService.isConsoleEmpty();
        $log.log("Turning onAir to: %s", vm.onAir);
        if (vm.onAir) {
            watchingService.startWatching(vm.selectedLog, needTail)
        } else {
            watchingService.stopWatching();
        }
    });
    // the following subscription allows AnaLog client app to retrieve the freshest log choices on server (re)starts
    $scope.$on('serverConnected', function () {
        choicesService();         // triggers 'choicesReady' event; it will also update the choices after server restart
    });
    // the following subscription is responsible for proper control of watching mode - it must be reactivated on changes
    $scope.$on('choicesReady', function (event, result) {
        $log.log("ChoicesReady: %o", result);
        $scope.choices = result.choices;
        vm.selectedLog = result.selectedChoice;
        $rootScope.watchingLog = result.selectedChoice.title + " - " + config.general.appTitle;

        if (!vm.onAir) {
            if (vm.launching) {         // if AnaLog's page is just loading let's start watching automatically
                vm.onAir = true;
                vm.launching = false;
            } // nothing should be done in this case as it is user's decision not to watch any log

        } else {    // i.e. watching has been acting for some time before server get connected (again)
            watchingService.stopWatching();
            watchingService.startWatching(vm.selectedLog, renderingService.isConsoleEmpty());
            // it is assumed that selectedLog always equals to previous one so that there is no need to clear console
        }

    });
    // to stop watching in case of server failure
    $scope.$on('serverFailure', function () {
        vm.onAir = false;
        // vm.launching = true;     // this may allow to reactivate watching even after server failure
    });
    // to explicitly stop watching and close server connection upon termination
    $scope.$on('$destroy', function() {
        vm.onAir = false;
        renderingService.stopTimer();
        watchingService.disconnect();
    });

});
