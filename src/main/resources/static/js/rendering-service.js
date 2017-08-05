app.factory('renderingService', ['$interval', '$log', function($interval) {
    var appendingRenderQueue = [];
    var prependingRenderQueue = [];
    var consolePanel = $('#consolePanel');
    var body = $('body');
    var isFirstPrependingString = false;

    function renderMessages() {
        if (appendingRenderQueue.length == 0 && prependingRenderQueue.length == 0) {
            return;
        }
        var i, nextLine;
        if (prependingRenderQueue.length > 0) {
            if (isFirstPrependingString) {
                consolePanel.find('div:first').replaceWith(prependingRenderQueue.pop());
                isFirstPrependingString = false;
            }

            for (i = 0; i < Math.ceil(prependingRenderQueue.length / 5); i++) {
                nextLine = prependingRenderQueue.pop();
                consolePanel.prepend(nextLine);
            }

            if (prependingRenderQueue.length == 0) {
                isFirstPrependingString = true;
            }
            angular.element(window).scrollTop(0);

        } else {
            var renderPart = Math.ceil(appendingRenderQueue.length/5);
            for (i = 0; i < renderPart; i++) {
                nextLine = appendingRenderQueue.shift();
                consolePanel.append(nextLine);
            }
            // $.smoothScroll({
            //     scrollTarget: '#scrollTarget',
            //     speed: 400
            //     // autoCoefficient: 2
            // });
            angular.element(window).scrollTop(body.height());
        }

    }
    function appendMessages(messages) {
        appendingRenderQueue.push.apply(appendingRenderQueue, messages);
    }
    function prependMessages(messages) {
        prependingRenderQueue.push.apply(prependingRenderQueue, messages);
    }
    function clearQueue() {
        appendingRenderQueue = [];
        consolePanel.empty();
    }

    $interval(renderMessages, 150);

    return {
        appendMessages: appendMessages,
        prependMessages: prependMessages,
        clearQueue: clearQueue
    }
}]);