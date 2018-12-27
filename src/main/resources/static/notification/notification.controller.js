/**
 * Controller responsible for showing notification balloon messages with information about various asynchronous events.
 */
app.controller('notificationController', ['$scope', '$log', '$interval', 'notifications',
    function ($scope, $log, $interval, notifications) {
        var vm = this;
        vm.message = undefined;
        vm.showing = false;
        vm.onceDisconnected = false;
        vm.intervalPromise = undefined;

        //<editor-fold desc="Server Events">
        $scope.$on('serverDisconnected', function () {
            vm.message = angular.copy(notifications['serverDisconnected']);
            vm.onceDisconnected = true;
            vm.showing = true;
        });
        $scope.$on('serverConnected', function () {
            if (!vm.onceDisconnected)
                return;
            vm.message = angular.copy(notifications['serverConnected']);
            vm.showing = true;
        });
        //</editor-fold>

        //<editor-fold desc="Tail Events">
        $scope.$on('fileNotFound', function (event, details) {
            vm.message = angular.copy(notifications['fileNotFound']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        $scope.$on('fileAppeared', function (event, details) {
            vm.message = angular.copy(notifications['fileAppeared']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        $scope.$on('fileRotated', function (event, details) {
            vm.message = angular.copy(notifications['fileRotated']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        $scope.$on('fileDisappeared', function (event, details) {
            vm.message = angular.copy(notifications['fileDisappeared']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        $scope.$on('fileTruncated', function (event, details) {
            vm.message = angular.copy(notifications['fileTruncated']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        //</editor-fold>

        //<editor-fold desc="Server Fault(s)">
        $scope.$on('serverFailure', function (event, details) {
            vm.message = angular.copy(notifications['serverFailure']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
        });
        $scope.$on('choicesNotFound', function (event, details) {
            vm.message = angular.copy(notifications['choicesNotFound']);
            vm.message.text = vm.message.text.format(details);
            vm.showing = true;
            vm.intervalPromise = $interval(vm.close, 5000, 1);
        });
        //</editor-fold>

        vm.close = function () {
            if (angular.isDefined(vm.intervalPromise)) {
                $interval.cancel(vm.intervalPromise);
                vm.intervalPromise = undefined;
            }
            vm.showing = false;
        };

    }]);