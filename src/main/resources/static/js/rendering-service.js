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
            renderCompositeMessages(newPart)
        } else {
            renderPlainMessages(newPart);
        }
    }

    function renderCompositeMessages(newPart) {
        $log.log("Preparing COMPOSITE messages: ", newPart);
        var $newRecord = $("<div></div>")
            .data("timestamp", newPart.timestamp)
            .addClass('node-'+newPart.sourceNode)
            .hide();

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
            $newRecord.append($messageLine);
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

    function renderPlainMessages(newPart) {
        console.log("Preparing PLAIN messages: ", newPart);
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
        $window.scrollTo("max", 400, {
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