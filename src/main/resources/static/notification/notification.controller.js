/**
 * Controller responsible for showing notification balloon messages with information about various asynchronous events.
 */
app.controller('notificationController', ['$scope', '$log', '$interval', 'notifications',
    function ($scope, $log, $interval, notifications) {
        let vm = this;
        const SHOW_DELAY = 12000;       // it's better to set it higher than websocket.reconnectDelayMs config value
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
        $scope.$on('fileNotFound', function (event, details) {
            vm.message = angular.copy(notifications['fileNotFound']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('fileAppeared', function (event, details) {
            vm.message = angular.copy(notifications['fileAppeared']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('fileRotated', function (event, details) {
            vm.message = angular.copy(notifications['fileRotated']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('fileDisappeared', function (event, details) {
            vm.message = angular.copy(notifications['fileDisappeared']);
            vm.message.text = vm.message.text.format(details);
            vm.startAutoHideTimer();
            vm.showing = true;
        });
        $scope.$on('fileTruncated', function (event, details) {
            vm.message = angular.copy(notifications['fileTruncated']);
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