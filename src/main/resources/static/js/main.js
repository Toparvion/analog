var currentLogFileName;
var appendingRenderQueue;
var prependingRenderQueue;
var $consolePanel;
var $updateNowButton;
var $body;
var $watchToggler;
var $loader;
var isLoading = false;
var isFirstPrependingString = false;
// переменные от функционала слежения
var isWatching = false;
var watcher;

function init() {
	timerId = null;
	appendingRenderQueue = [];
	prependingRenderQueue = [];
	$consolePanel = $('#consolePanel');
	$body = $('body');
    $updateNowButton = $('button[name=updateNowButton]');
    $watchToggler = $('input[name=watchToggler]');
    $loader = $('#loader');

    // извлекаем имя лога из URL-параметра log
    var logFileName = '/pub/home/upc/applications/upcManualTesting/log/bankplus.log';
    var $select = $('select[name=logFileName]');
    if(location.search) {
        var pairs = (location.search.substr(1)).split('&');
        for(var i = 0; i < pairs.length; i ++) {
            var param = pairs[i].split('=');
            if (param[0] == 'log') {
                logFileName = param[1];
                var displayName = logFileName.substring(logFileName.lastIndexOf("/")+1);
                var customPath = $('<option selected="selected" value="'+logFileName+'">'+displayName+'</option>');
                $select.append(customPath);
            }
        }
    }
    currentLogFileName = $select.val();

    setNewLogFileName();
	setInterval(renderNextMessage, 150);

    watcher = {};
    watcher.socket = null;
}

function loadData(prependingMode) {
	if (isLoading == true) {
		return;
	}	
	isLoading = true;
    $loader.addClass('loading');
    var url = 'provide?log='+currentLogFileName;
    url += "&encoding=" + $('select[name=encoding]').val();
    if (prependingMode) {
        var prependingSize = $('select[name=prependingSize]').val();
        url = url + '&prependingSnippetSizePercent=' + prependingSize;
        $('#prependingMode').attr('disabled', 'disabled');
    }
    // url = url +'&callback=?';
    $.ajax({
        url:url,
        dataType:'json'})
        .done(function (responseData) {
            if (responseData.items.length > 0) {
                prepareMessages(responseData, prependingMode);
            }
        })
        .fail(function (jqXHR, textStatus, errorThrown) {
            stopAutoRefreshing();
            stopWatching();
            alert('Не удалось загрузить файл лога: ' + textStatus + '(' + errorThrown + ')');
        })
        .always(function () {
            if (prependingMode) {
                $('#prependingMode').removeAttr('disabled');
            }
            $loader.removeClass('loading');
			isLoading = false;
        })
    ;
}

function prepareMessages(responseData, prependingMode) {
	$.each(responseData.items, function(i, item) {
		var $messageLine;
		if (item.level != 'XML') {
			$messageLine = $('<div class="' + item.level + '">' + item.text + '</div>');
		} else {
			$messageLine = $('<pre class="xml"><code>' + item.text + '</code></pre>');
            hljs.highlightBlock($messageLine[0]);
		}
        // выбираем заполняемую очередь строк в зависимости от направления заполнения
        if (prependingMode) {
            prependingRenderQueue.push($messageLine);
        } else {
            appendingRenderQueue.push($messageLine);
        }
	});
}

function setupTimer() {
	var updatePeriod = 1000;// $('select[name=updatePeriod]').val();
	if ($('input[name=updateToggler]').attr('checked') != 'checked') {
		clearInterval(timerId);
		return;
	}
	clearInterval(timerId);
    timerId = setInterval(
        function () {
            loadData();
        },
        updatePeriod
    );
    loadData();
}

function setNewLogFileName() {
    stopWatching();
	var logFileName = $('select[name=logFileName]').val();
	if (logFileName != currentLogFileName) {
		$consolePanel.empty();
		currentLogFileName = logFileName;
	}
	// loadData();
}

function renderNextMessage() {
    if (appendingRenderQueue.length == 0 && prependingRenderQueue.length == 0) {
        return;
    }
    var i, nextLine;
    // предваряющая очередь имеет приоритет над дополняющей, поэтому анализируется и обрабатывается первой
    if (prependingRenderQueue.length > 0) {
        if (isFirstPrependingString) {
            $consolePanel.find('div:first').replaceWith(prependingRenderQueue.pop());
            isFirstPrependingString = false;
        }

        for (i = 0; i < Math.ceil(prependingRenderQueue.length/5); i++) {
            nextLine = prependingRenderQueue.pop();
            $consolePanel.prepend(nextLine);
        }

        if (prependingRenderQueue.length == 0) {
            isFirstPrependingString = true;
        }
        $(window).scrollTop(0);
    } else {
        for (i = 0; i < Math.ceil(appendingRenderQueue.length/5); i++) {
            nextLine = appendingRenderQueue.shift();
            $consolePanel.append(nextLine);
        }
    	$(window).scrollTop($body.height());
    }
}

function prependLog() {
    stopAutoRefreshing();
    stopWatching();
    loadData(true);
}

function stopWatching() {
   if (isWatching == true) {
       toggleWatching();
   }
}

function stopAutoRefreshing() {
    clearInterval(timerId);
    $('input[name=updateToggler]').removeAttr('checked');
}

function toggleWatching() {
    if (!isWatching) {
        console.log('Turning log watching on...');
        var url = 'ws://' + window.location.host + '/analog/watch?log=' + encodeURIComponent(currentLogFileName)        // TODO отвязаться
            + '&encoding=' + $('select[name=encoding]').val();
        if ('WebSocket' in window) {
            watcher.socket = new WebSocket(url);
        } else if ('MozWebSocket' in window) {
            watcher.socket = new MozWebSocket(url);
        } else {
            console.error('Error: WebSocket is not supported by this browser.');
            return;
        }

        watcher.socket.onopen = function () {
            console.log("Connection opened.");
            //watcher.socket.send(currentLogFileName);
            $updateNowButton.html("[Режим слежения]");
            $updateNowButton.attr('disabled', 'disabled');
            $loader.addClass('loading');
            isWatching = true;
        };

        watcher.socket.onclose = function () {
            console.log("Connection closed.");
            $watchToggler.removeAttr('checked');
            $updateNowButton.removeAttr('disabled');
            $updateNowButton.html("Обновить сейчас");
            $loader.removeClass('loading');
            isWatching = false;
        };

        watcher.socket.onerror = function(error) {
            alert("Ошибка слежения: " + error.message);
            $watchToggler.removeAttr('checked');
            $updateNowButton.removeAttr('disabled');
            $updateNowButton.html("Обновить сейчас");
            $loader.removeClass('loading');
            isWatching = false;
        };

        watcher.socket.onmessage = function (message) {
            console.log("Message received.");
            var messagaData = JSON.parse(message.data);
            prepareMessages(messagaData, false);
        };

    } else {
        console.log('Turning log watching off...');
        watcher.socket.close();
        watcher.socket = null;
        isWatching = false;
    }
}