package org.wso2.extension.siddhi.io.prometheus.source;

import org.wso2.siddhi.core.exception.SiddhiAppCreationException;

/**
 * This Exception is to be thrown in Prometheus Source.
 */
public class PrometheusSourceException extends SiddhiAppCreationException {

    public PrometheusSourceException(String message) {
        super(message);
    }

    public PrometheusSourceException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public PrometheusSourceException(Throwable throwable) {
        super(throwable);
    }

}
