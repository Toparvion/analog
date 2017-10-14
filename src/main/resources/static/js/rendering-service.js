app.factory('renderingService', ['$log', function($log) {
    var $window = $(window);
    var $document = $(document);
    var $body = $('body');
    var $consolePanel = $('#consolePanel');
    /**
     * When the service is about to render a record it first checks for reaching of the viewport bottom to decide
     * whether scrolling down should be applied or not. It works perfectly, almost always. But if there are several
     * consequent records in a short period of time then some of then may check the reaching of bottom while some
     * others are doing their animation. It causes checking to lie and scrolling doesn't get applied. To prevent such
     * behavior the following additional variable is introduced.
     * @type {boolean}
     */
    var isAnimating = false;

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
            $consolePanel.append($newRecord);
        }

        render($newRecord);
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
        render($partLines);
    }

    function scrollDown() {
        $window.scrollTo("max", 400, {
            easing: 'swing',
            axis: 'y',
            start: function() {isAnimating=true},
            always: function () {isAnimating=false}
        });
    }

    // Animated output logic:
    function render($newRecord) {
        var isConsoleUnScrollable = ($body.height() < $window.height());
        var isScrolledToBottom = ($window.scrollTop() === ($document.height() - $window.height()));
        if (isConsoleUnScrollable) {       // should we use slide animation to output new record?
            $newRecord.slideDown(400, scrollDown);
            /* In most cases scrolling down before the viewport is totally filled has no effect. But if the new record
             * comes out of viewport, it's end won't be visible. This is the only case that makes us use
             * scrolling animation regardless of actual scrolling presence. */
        } else if (isScrolledToBottom || isAnimating) {     // should we smoothly scroll down?
            /* Because new record initially appears out of visible area, there is no need to use slide
             * animation. Instead we render it immediately and then make visible by scrolling the viewport down.*/
            $newRecord.slideDown(0, scrollDown);
        } else {
            $newRecord.show();    // when user manually scrolled up, we just "collect" new records at the bottom
        }
    }

    function clearQueue() {
        $consolePanel.empty();
    }

    return {
        clearQueue: clearQueue,
        scrollDown: scrollDown,
        renderCompositeMessages: renderCompositeMessages,
        renderPlainMessages: renderPlainMessages
    }
}]);