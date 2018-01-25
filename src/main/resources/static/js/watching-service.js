app.factory('watchingService', ['$log', '$rootScope', 'renderingService', 'config',
                        function ($log, $rootScope, renderingService, config) {
    var stompClient = undefined;
    var subscription = undefined;


    function connect() {
        if (angular.isDefined(stompClient)) {
            return;             // to prevent double connection
        }
        stompClient = Stomp.over(function () {
            return new SockJS(config.websocket.watchEndpoint);
        });
        stompClient.reconnect_delay = config.websocket.reconnectDelayMs;     // to repeat failed connection attempts
        stompClient.connect({},
            function () {
                $log.log('Watching service has connected to the server.');
                $rootScope.$broadcast('serverConnected');
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
            },
            function () {
                // $log.log('Watching service failed to connect to the server.');     // to avoid flooding the console
                $rootScope.$broadcast('serverDisconnected');
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
            });
    }

    function startWatching(selectedLog, isTailNeeded) {
        var logId, isPlain;
        if (selectedLog.uid) {
            logId = selectedLog.uid;
            isPlain = false;
        } else {
            logId = selectedLog.path;
            isPlain = true;
        }
        var headers = {
            isPlain: isPlain,
            isTailNeeded: isTailNeeded
        };
        subscription = stompClient.subscribe(config.websocket.topicPrefix + logId, onServerMessage, headers);
        $log.log("New subscription has been created with headers: %o", headers)
    }

    function onServerMessage(message) {
        var payload = JSON.parse(message.body);
        var messageType = message.headers['type'];

        switch (messageType) {
            case 'RECORD':
                renderingService.render(payload);
                break;
            case 'METADATA':
                $rootScope.$broadcast(payload.eventType, payload);
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
                break;
            case 'FAILURE':
                $rootScope.$broadcast('serverFailure', payload);
                $rootScope.$apply();        // since we're not in "Angular realm", we need to trigger it manually
                break;
            default:
                $log.log("ERROR: received a message of unknown type: " + payload);
        }
    }

    function stopWatching() {
        if (angular.isDefined(subscription)) {
            subscription.unsubscribe();  // this causes an exception in case of triggering during server disconnection
            subscription = undefined;
            $log.log("Subscription has been removed.")
        }
    }

    function disconnect() {
        stopWatching();
        stompClient.disconnect(function () {
            $log.log("Watching service has disconnected from peer.");
        });
        stompClient = undefined;
    }

    return {
        connect: connect,
        startWatching: startWatching,
        stopWatching: stopWatching,
        disconnect: disconnect
    }
}]);