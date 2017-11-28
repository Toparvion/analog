/**
 * Frontend configuration properties.
 * TODO retrieve them from the server with the help of template engine like Thymeleaf
 */
app.constant('config', {
    general: {
        appTitle: 'АнаЛ&oacute;г'
    },

    rendering: {
        /** Period between successive renderings of records accumulated in the queue */
        periodMs: 1000
    },

    websocket: {
        topicPrefix: "/topic/",
        watchEndpoint: '/watch-endpoint',
        reconnectDelayMs: 10000
    }
});