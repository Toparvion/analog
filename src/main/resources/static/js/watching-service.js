app.factory('watchingService', ['$log', '$rootScope', 'renderingService',
                        function ($log, $rootScope, renderingService) {
    var stompClient = Stomp.over(function () {
        return new SockJS('/watch-endpoint');
    });
    var subscription = undefined;


    function connect() {
        stompClient.reconnect_delay = 10000;     // to repeat failed connection attempts every 10 seconds
        stompClient.connect({},
            function () {
                $log.log('Watching service has connected to the server.');
                $rootScope.$broadcast('serverConnected');
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
            },
            function () {
                $log.log('Watching service failed to connect to the server.');
                $rootScope.$broadcast('serverDisconnected');
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
            });
    }

    function startWatching(selectedLog) {
        var logId, isPlain;
        if (selectedLog.uid) {
            logId = selectedLog.uid;
            isPlain = false;
        } else {
            logId = selectedLog.path;
            isPlain = true;
        }
        subscription = stompClient.subscribe('/topic/' + logId, onServerMessage, {isPlain: isPlain});
    }

    function onServerMessage(message) {
        var newPart = JSON.parse(message.body);
        if (newPart.timestamp) {
            renderingService.renderCompositeMessages(newPart)
        } else {
            renderingService.renderPlainMessages(newPart);
        }
    }

    function stopWatching() {
        if (angular.isDefined(subscription)) {
            subscription.unsubscribe();
            subscription = undefined;
            $log.log("Subscription has been removed.")
        }
    }

    function disconnect() {
        stopWatching();
        stompClient.disconnect(function () {
            $log.log("Watching service has disconnected from peer.");
        });
    }

    return {
        connect: connect,
        startWatching: startWatching,
        stopWatching: stopWatching,
        disconnect: disconnect
    }
}]);