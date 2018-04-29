app.factory('renderingService', ['$log', '$interval', 'config', function($log, $interval, config) {
    var $window = $(window);
    var $document = $(document);
    var $body = $('body');
    var $consolePanel = $('#consolePanel');

    var renderingQueue = [];
    var intervalPromise;
    var consoleIsEmpty = true;    // a flag indicating that the console is clean (i.e. contains no records)
    init();

    function init() {
        intervalPromise = $interval(animate, config.rendering.periodMs, /*count:*/0, /*invokeApply:*/false);
    }

    function preRender(newPart) {
        if (angular.isDefined(newPart.timestamp)) {
            prepareCompositeMessages(newPart)
        } else {
            preparePlainMessages(newPart);
        }
        consoleIsEmpty = false;
    }

    function prepareCompositeMessages(newPart) {
        $log.log("Preparing COMPOSITE record: %o", newPart);
        var $newRecord = $('<div></div>')
            .addClass('composite-record')
            .addClass('highlight-'+ newPart.highlightColor)
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
        var $records = $consolePanel.find("> .composite-record");
        var $precedingRecord;
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
            if ($records.length > 0) {
                $consolePanel.prepend($newRecord);          // for the earliest record
            } else {
                $consolePanel.append($newRecord);           // for the very first insertion only
            }
        }

        renderingQueue.push($newRecord);
    }

    function preparePlainMessages(newPart) {
        $log.log("Preparing PLAIN records: %o", newPart);
        var $recordsBunch = $("<div></div>")
                         // .addClass('plain-record')            // no need for the time being
                            .hide();
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
                $messageLine = $("<pre></pre>")
                    .append($code);
                hljs.highlightBlock($messageLine[0]);
            }
            $recordsBunch.append($messageLine);
        });
        $consolePanel.append($recordsBunch);
        renderingQueue.push($recordsBunch);
    }

    /**
     * Animated output logic
     */
    function animate() {
        // first let's check if it's time to remove the oldest records to avoid client's memory exhaustion
        var $allRecords = $consolePanel.find("> div");
        var isComposite = $allRecords.hasClass('composite-record');
        var threshold = isComposite
            ? config.rendering.eviction.composite.threshold
            : config.rendering.eviction.plain.threshold;
        var partSize = isComposite
            ? config.rendering.eviction.composite.depth
            : config.rendering.eviction.plain.depth;
        var recordsCount = $allRecords.length;
        if (recordsCount > threshold) {
            var $recordsToRemove = $allRecords.slice(0, partSize);
            $recordsToRemove.remove();
            $log.log('Removed first %d %s records as their total count %d exceeds threshold value %d.',
                partSize, (isComposite?"composite":"plain"), recordsCount, threshold);
        }

        // now it's time to show the new records
        var isConsoleUnScrollable = ($body.height() < $window.height());
        var isScrolledToBottom = ($window.scrollTop() === ($document.height() - $window.height()));
        while (renderingQueue.length > 0) {
            var $newRecord = renderingQueue.shift();
            if (isConsoleUnScrollable && renderingQueue.length === 1) {// should slide animation to output new record?
                $newRecord.slideDown(400, scrollDown);
                /* In most cases scrolling down before the viewport is totally filled has no effect. But if the new
                 * record comes out of viewport, its end won't be visible. This is the only case that makes AnaLog use
                 * scrolling animation regardless of actual scrolling presence. */

            } else {
                // when user manually scrolled up, we just "collect" new records at the bottom
                $newRecord.show(0, function () {
                    // and scroll to bottom if necessary
                    if (renderingQueue.length === 0 && isScrolledToBottom)
                        scrollDown();
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

    function clearConsole() {
        $consolePanel.empty();
        consoleIsEmpty = true;
    }

    function stopTimer() {
        $interval.cancel(intervalPromise);
    }

    return {
        clearConsole: clearConsole,
        scrollDown: scrollDown,
        stopTimer: stopTimer,
        render: preRender,
        isConsoleEmpty: function () {return consoleIsEmpty;}
    }
}]);