/**
 * Controller responsible for showing notification balloon messages with information about various asynchronous events.
 */
app.controller('notificationController', ['$scope', '$log', 'notifications',
                                 function ($scope, $log, notifications) {
    var vm = this;
    vm.message = undefined;
    vm.showing = false;
    vm.onceDisconnected = false;

    //<editor-fold desc="Server Events">
    $scope.$on('serverDisconnected', function () {
        vm.message = notifications['serverDisconnected'];
        vm.onceDisconnected = true;
        vm.showing = true;
    });
    $scope.$on('serverConnected', function () {
        if (!vm.onceDisconnected)
            return;
        vm.message = notifications['serverConnected'];
        vm.showing = true;
    });
    //</editor-fold>

    //<editor-fold desc="Tail Events">
    $scope.$on('fileNotFound', function (event, details) {
        vm.message = notifications['fileNotFound'];
        vm.message.text = vm.message.text.format(details);
        vm.showing = true;
    });
    $scope.$on('fileAppeared', function (event, details) {
        vm.message = notifications['fileAppeared'];
        vm.message.text = vm.message.text.format(details);
        vm.showing = true;
    });
    $scope.$on('fileDisappeared', function (event, details) {
        vm.message = notifications['fileDisappeared'];
        vm.message.text = vm.message.text.format(details);
        vm.showing = true;
    });
    $scope.$on('fileTruncated', function (event, details) {
        vm.message = notifications['fileTruncated'];
        vm.message.text = vm.message.text.format(details);
        vm.showing = true;
    });
    //</editor-fold>

    //<editor-fold desc="Server Fault(s)">
    $scope.$on('serverFailure', function (event, details) {
         vm.message = notifications['serverFailure'];
         vm.message.text = vm.message.text.format(details);
         vm.showing = true;
    });
    $scope.$on('choicesNotFound', function (event, details) {
         vm.message = notifications['choicesNotFound'];
         vm.message.text = vm.message.text.format(details);
         vm.showing = true;
    });
    //</editor-fold>

    vm.close = function () {
        vm.showing = false;
    };

}]);