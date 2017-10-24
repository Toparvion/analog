/**
 * Controller responsible for showing notification balloon messages with information about various asynchronous events.
 * Subject to localize.
 */
app.controller('notificationController', function ($scope) {
    var vm = this;
    vm.message = undefined;
    vm.showing = false;
    vm.onceDicsonnected = false;

    $scope.$on('serverDisconnected', function () {
        vm.message = {
            level: 'warning',
            title: 'Ой-ой!..',
            text: 'Нет связи с сервером. Как восстановится, сообщу.'
        };
        vm.onceDicsonnected = true;
        vm.showing = true;
    });

    $scope.$on('serverConnected', function () {
        if (!vm.onceDicsonnected)
            return;
        vm.message = {
            level: 'success',
            title: 'Ура!',
            text: 'Связь с сервером восстановлена. Можно работать.'
        };
        vm.showing = true;
    });

});