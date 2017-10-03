app = angular.module("AnaLog", ['ngSanitize', 'ui.select']);

app.run(function ($rootScope) { $rootScope.watchingLog = "АнаЛог v0.7 (загрузка...)"; });

app.controller('mainController', function ($scope, $rootScope,
                                           choicesService, providerService, renderingService,
                                           $location, $log, $interval) {
    var vm = this;

    var onAirPromise;
    var watching = null;
    $scope.encoding = 'utf8';
    $scope.onAir = false;
    $scope.textWrap = true;
    $scope.prependingSize = "1";
    $scope.choices = [];
    $scope.autoScroll = true;

    vm.selectedLog = undefined;
    vm.previousScrollTop = 0;       // needed to distinguish user scroll (up only) from programmatic scroll (anywhere)

    initChoicesAndLog();

    $(window).scroll(function () {
        var currentScrollTop = $(window).scrollTop();
        if (currentScrollTop === ($(document).height() - $(window).height())) {
            $scope.autoScroll = true;
        } else if (currentScrollTop < vm.previousScrollTop) {
            // supposing that only user might scroll the view up
            $scope.autoScroll = false;
        }
        vm.previousScrollTop = currentScrollTop;        // to compare at the next invocation
    });
    vm.onLogChange = function() {
        $log.log("New choice: " + vm.selectedLog.path + " (" + vm.selectedLog.encoding + ")");
        $scope.encoding = vm.selectedLog.encoding;
        $rootScope.watchingLog = vm.selectedLog.title + " - АнаЛ&oacute;г";
        $location.path(vm.selectedLog.path);
        $scope.onAir = false;
        renderingService.clearQueue();
        $scope.updateLog(/*readBackAllowed:*/true);
    };
    /**
     * Sliding output is sensible when the new record is in visible area only.
     * @returns {boolean} true if console panel (body) is not getting scrolled yet
     */
    $scope.slidingOutput = function () {
        // this is the same as determining if scroll bar is acting but more simple
        return $("body").height() < $(window).height();
    };
    $scope.updateLog = function (isReadBackAllowed) {
        providerService(vm.selectedLog.path, $scope.encoding, $scope.stopOnAir, isReadBackAllowed)
    };
    $scope.disableOnAirPoller = function() {
        if (angular.isDefined(onAirPromise)) {
            $interval.cancel(onAirPromise);
            onAirPromise = undefined;
            $log.log("Auto updating timer has been stopped.");
        }
    };
    $scope.$on('$destroy', function() {
        $scope.disableOnAirPoller();
    });
    $scope.prepend = function () {
        $scope.onAir = false;
        providerService(vm.selectedLog.path, $scope.encoding, $scope.stopOnAir, false, $scope.prependingSize);
    };
    $scope.clear = function () {
        renderingService.clearQueue();
    };
    $scope.stopOnAir = function () {
        $scope.onAir = false;
    };
    // the following watch allows us to react to URL path change instantly (without opening a new browser tab)
    $scope.$watch(function () {
        return $location.path();
    }, function (value) {
        // $log.log("Raw value: " + value); // helpful for troubleshooting paths starting with 'C:\'
        var newPath = value;
        if (vm.selectedLog && !arePathsEqual(vm.selectedLog.path, newPath)) {
            $log.log("Path change detected from: '" + vm.selectedLog.path + "' to: '" + newPath +"'.");
            $scope.onAir = false;
            $scope.clear();
            initChoicesAndLog();
        }
    });
    // the following watch allows us to react to any change of onAir mode flag (both from UI or internally)
    $scope.$watch(function () {
        return $scope.onAir;
    }, function () {
        $log.log("Turning onAir to: " + $scope.onAir);
        if ($scope.onAir) {
            // onAirPromise = $interval(function() {$scope.updateLog(false)}, 1000);
            // $scope.updateLog(false);    // in order not to wait for the first interval triggering
            // TODO extract to separate Angular service
            watching = {};
            watching.stompClient = Stomp.over(new SockJS('/watch-endpoint'));
            watching.stompClient.connect({}, function (frame) {
                $log.log('Connected: ' + frame);
                var callback = function (serverMessage) {
                    var newPart = JSON.parse(serverMessage.body);
                    var preparedLines = newPart.timestamp
                        ? prepareCompositeMessages(newPart)
                        : preparePlainMessages(newPart);
                    renderingService.appendMessages(preparedLines)
                };
                var subId, logId;
                if (vm.selectedLog.uid) {
                    logId = vm.selectedLog.uid;
                    subId = 'uid=' + logId;
                } else {
                    logId = vm.selectedLog.path;
                    subId = 'path=' + logId;
                }
                watching.subscription = watching.stompClient.subscribe('/topic/'+logId, callback, {id: subId});
            });

        } else {
            // $scope.disableOnAirPoller();
            if (watching !== null) {
                watching.subscription.unsubscribe();
                watching.stompClient.disconnect(function () {watching = null});
            }
        }
    });

    /**
     * Queries log choice options (choices) from server and initiates loading of selected log.
     */
    function initChoicesAndLog() {
        choicesService(function (choices, selectedChoice) {
            $scope.choices = choices;
            vm.selectedLog = selectedChoice;
            $scope.encoding = selectedChoice.encoding;
            $rootScope.watchingLog = selectedChoice.title + " - АнаЛог";
            $scope.updateLog(/*readBackAllowed:*/true);
        });
    }

    // TODO extract to separate Angular service
    function prepareCompositeMessages(newPart) {
        $log.log("Preparing COMPOSITE messages: ", newPart);
        var preparedMessages = [];
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
        var $consolePanel = $("#consolePanel");
        var $records = $consolePanel.find("> div"), $precedingRecord;
        for (var i = $records.length; i-- > 0;) {
            if (jQuery.data($records[i], 'timestamp') <= newPart.timestamp) {
                $precedingRecord = $($records[i]);
                break;
            }
        }
        if ($precedingRecord) {
            $precedingRecord.after($newRecord);
        } else {
            $consolePanel.append($newRecord);
        }
        // Animated output logic:
        if ($scope.slidingOutput()) {       // should we use slide animation to output new record?
            $newRecord.slideDown(400, $scope.scrollDown);
            /* In most cases scrolling down before the viewport is totally filled has no effect. But if the new record
            * comes out of viewport it's end won't be visible. This is the only case that makes us use
            * scrolling animation regardless of actual scrolling presence. */
        } else if ($scope.autoScroll) {     // should we smoothly scroll down?
            /* Because new record initially appears out of (under) visible area, there is no need to use slide
             animation. Instead we render it immediately and then make visible by scrolling down.*/
            $newRecord.slideDown(0, $scope.scrollDown);
        } else {
            $newRecord.slideDown(0);    // when user manually scrolled up, we just "collect" new records at the bottom
        }

        // return jQuery.hasData(this) && $(this).data('timestamp') > newPart.timestamp;    // for '.filter()'
        // preparedMessages.push($newRecord);
        return preparedMessages;
    }

    $scope.scrollDown = function () {
        $(window).scrollTo("max", 400, {easing: 'swing', axis: 'y'});
    }

});
