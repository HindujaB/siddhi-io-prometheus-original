package org.wso2.extension.siddhi.io.prometheus.source;

import org.wso2.siddhi.core.exception.SiddhiAppCreationException;

import java.util.Locale;

/**
 * This specifies the metric types that are supported by Prometheus
 */
public enum MetricType {

    COUNTER,
    GAUGE,
    SUMMARY,
    HISTOGRAM;

    public static MetricType assignMetricType(String metricTypeString) {
        MetricType metricType;
        switch (metricTypeString.trim().toUpperCase(Locale.ENGLISH)) {
            case "COUNTER": {
                metricType = MetricType.COUNTER;
                break;
            }
            case "GAUGE": {
                metricType = MetricType.GAUGE;
                break;
            }
            case "HISTOGRAM": {
                metricType = MetricType.HISTOGRAM;
                break;
            }
            case "SUMMARY": {
                metricType = MetricType.SUMMARY;
                break;
            }
            default: {
                throw new SiddhiAppCreationException("Metric type contains illegal value");
            }
        }
        return metricType;
    }

    public static String getMetricTypeString(MetricType metricType) {
        switch (metricType) {
            case COUNTER: {
                return "counter";
            }
            case GAUGE: {
                return "gauge";
            }
            case HISTOGRAM: {
                return "histogram";
            }
            case SUMMARY: {
                return "summary";
            }
            default: {
                throw new RuntimeException("Metric type contains illegal value");
            }
        }
    }
}

