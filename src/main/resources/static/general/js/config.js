/**
 * Frontend configuration properties.
 * TODO retrieve them from the server with the help of template engine like Thymeleaf
 */
app.constant('config', {
    general: {
        appTitle: 'AnaL&oacute;g'
    },

    rendering: {
        /** Period between successive renderings of records accumulated in the queue */
        periodMs: 1000,
        eviction: {
            composite: {
                threshold: 2200,
                depth: 200
            },
            plain: {
                threshold: 1000,
                depth: 100
            }
        }
    },

    websocket: {
        topicPrefix: "/topic/",
        watchEndpoint: '/watch-endpoint',
        reconnectDelayMs: 5000
    },

    mappings: {
        type2class: new Map([
            ['LOCAL_FILE', 'primary'],
            ['NODE', 'info'],
            ['DOCKER', 'success'],
            ['KUBERNETES', 'danger'],
            ['K8S', 'danger'],
            ['COMPOSITE', 'warning']
        ])
    }
});