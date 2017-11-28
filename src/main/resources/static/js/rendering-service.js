app.factory('renderingService', ['$log', '$interval', 'config', function($log, $interval, config) {
    var $window = $(window);
    var $document = $(document);
    var $body = $('body');
    var $consolePanel = $('#consolePanel');

    var renderingQueue = [];
    var intervalPromise;
    init();

    function init() {
        intervalPromise = $interval(animate, config.rendering.periodMs, /*count:*/0, /*invokeApply:*/false);
    }

    function render(newPart) {
        if (angular.isDefined(newPart.timestamp)) {
            prepareCompositeMessages(newPart)
        } else {
            preparePlainMessages(newPart);
        }
    }

    function prepareCompositeMessages(newPart) {
        $log.log("Preparing COMPOSITE messages: %o", newPart);
        var fileName = extractFileName(newPart.sourcePath).replace('.log', '');
        var $newRecord = $('<div></div>')
            .addClass('composite-record')
            .addClass('file-'+ fileName)
            .data('timestamp', newPart.timestamp)
            .hide();

        var $marker = $('<div></div>')
            .addClass('marker')
            .attr('data-balloon', ('[' + newPart.sourceNode + '] ' + newPart.sourcePath))
            .attr('data-balloon-pos', 'right');
        $newRecord.append($marker);

        var $payload = $('<div></div>')
            .addClass('payload');
        $newRecord.append($payload);

        angular.forEach(newPart.lines, function (line) {
            var $messageLine;
            if (line.style === 'XML') {
                var $code = $("<code></code>")
                    .addClass("xml")
                    .html(line.text);
                $messageLine = $("<pre></pre>").append($code);
                hljs.highlightBlock($messageLine[0]);
            } else {
                $messageLine = $("<div></div>")
                    .addClass(line.style)
                    .html(line.text);
            }
            $payload.append($messageLine);
        });
        // determine correct position to insert new record
        var $records = $consolePanel.find("> div"), $precedingRecord;
        for (var i = $records.length; i-- > 0;) {
            if (jQuery.data($records[i], 'timestamp') <= newPart.timestamp) {
                $precedingRecord = $($records[i]);
                break;
            }
        }
        // and use the position to insert new record
        if ($precedingRecord) {
            $precedingRecord.after($newRecord);
        } else {
            $consolePanel.append($newRecord);       // for the very first insertion only
        }

        renderingQueue.push($newRecord);
    }

    function preparePlainMessages(newPart) {
        console.log("Preparing PLAIN messages: %o", newPart);
        var $partLines = $("<div></div>").hide();
        angular.forEach(newPart.lines, function (line) {
            var $messageLine;
            if (line.style !== 'XML') {
                $messageLine = $("<div></div>")
                    .addClass(line.style)
                    .html(line.text);
            } else {
                var $code = $("<code></code>")
                    .addClass("xml")
                    .html(line.text);
                $messageLine = $("<pre></pre>").append($code);
                hljs.highlightBlock($messageLine[0]);
            }
            $partLines.append($messageLine);
        });
        $consolePanel.append($partLines);
        renderingQueue.push($partLines);
    }

    /**
     * Animated output logic
     */
    function animate() {
        var isConsoleUnScrollable = ($body.height() < $window.height());
        var isScrolledToBottom = ($window.scrollTop() === ($document.height() - $window.height()));

        while (renderingQueue.length > 0) {
            var $newRecord = renderingQueue.shift();
            if (isConsoleUnScrollable) {       // should we use slide animation to output new record?
                $newRecord.slideDown(400, scrollDown);
                /* In most cases scrolling down before the viewport is totally filled has no effect. But if the new
                 * record comes out of viewport, its end won't be visible. This is the only case that makes AnaLog use
                 * scrolling animation regardless of actual scrolling presence. */

            } else {
                // when user manually scrolled up, we just "collect" new records at the bottom
                $newRecord.show(0, function () {
                    // and scroll to bottom if necessary
                    if (renderingQueue.length === 0 && isScrolledToBottom) scrollDown();
                });
            }
        }
    }

    function scrollDown() {
        $window.scrollTo("max", 300, {
            easing: 'swing',
            axis: 'y',
            interrupt: true
        });
    }

    function clearQueue() {
        $consolePanel.empty();
    }

    function stopTimer() {
        $interval.cancel(intervalPromise);
    }

    return {
        clearQueue: clearQueue,
        scrollDown: scrollDown,
        stopTimer: stopTimer,
        render: render
    }
}]);