/**
 * Controller responsible for showing notification balloon messages with information about various asynchronous events.
 */
app.controller('notificationController', ['$scope', '$log', '$interval', 'notifications',
    function ($scope, $log, $interval, notifications) {
        let vm = this;
        const SHOW_DELAY = 10000;       // it's better to set it higher than websocket.reconnectDelayMs config value
        vm.message = undefined;
        vm.showing = false;
        vm.onceDisconnected = false;
        vm.intervalPromise = undefined;

        //<editor-fold desc="Server Events">
        $scope.$on('serverDisconnected', function () {
            vm.message = angular.copy(notifications['serverDisconnected']);
            vm.onceDisconnected = true;
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('serverConnected', function () {
            if (!vm.onceDisconnected)
                return;
            vm.message = angular.copy(notifications['serverConnected']);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        //</editor-fold>

        //<editor-fold desc="Tail Events">
        $scope.$on('logNotFound', function (event, details) {
            vm.message = angular.copy(notifications['logNotFound']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('logAppeared', function (event, details) {
            vm.message = angular.copy(notifications['logAppeared']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('logRotated', function (event, details) {
            vm.message = angular.copy(notifications['logRotated']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('logDisappeared', function (event, details) {
            vm.message = angular.copy(notifications['logDisappeared']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('logTruncated', function (event, details) {
            vm.message = angular.copy(notifications['logTruncated']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('unrecognized', function (event, details) {
            vm.message = angular.copy(notifications['unrecognized']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        //</editor-fold>

        //<editor-fold desc="Server Fault(s)">
        $scope.$on('serverFailure', function (event, details) {
            vm.message = angular.copy(notifications['serverFailure']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('choicesNotFound', function (event, details) {
            vm.message = angular.copy(notifications['choicesNotFound']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        //</editor-fold>

        vm.close = function () {
            vm.stopAutoHideTimer();
            vm.showing = false;
        };

        //<editor-fold desc="Automatic Hiding Timer">
        vm.startAutoHideTimer = function () {
            vm.stopAutoHideTimer();         // to prevent double starting of timer
            vm.intervalPromise = $interval(vm.close, SHOW_DELAY, 1);
        };

        vm.stopAutoHideTimer = function () {
            if (angular.isDefined(vm.intervalPromise)) {
                $interval.cancel(vm.intervalPromise);
                vm.intervalPromise = undefined;
            }
        };

        $scope.$on('$destroy', vm.stopAutoHideTimer);
        //</editor-fold>

    }]);