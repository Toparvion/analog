app.directive('hideUponAutoScroll', function ($window, $rootScope, $log) {
    let previousScrollTop = 0;       // needed to distinguish user scroll (up only) from programmatic scroll (anywhere)
    let isAutoScroll = true;

    function link(scope, element) {
        angular.element($window).bind("scroll", function () {
            const currentScrollTop = $(window).scrollTop();
            if (!isAutoScroll && (currentScrollTop === ($(document).height() - $(window).height()))) {
                isAutoScroll = true;
                element.addClass('ng-hide');
                $log.log('autoScroll switched to true');
            } else if (isAutoScroll && (currentScrollTop < previousScrollTop) && (currentScrollTop > 0)) {
                // supposing that only user might scroll the view up
                isAutoScroll = false;
                element.removeClass('ng-hide');
                $log.log('autoScroll switched to false');
            }
            previousScrollTop = currentScrollTop;        // to compare at the next invocation
        });
    }

    return {
        restrict: 'A',
        link: link
    };
});