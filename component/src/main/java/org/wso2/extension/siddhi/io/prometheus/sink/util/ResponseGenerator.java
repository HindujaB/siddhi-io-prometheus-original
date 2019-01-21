package org.wso2.extension.siddhi.io.prometheus.sink.util;

import io.prometheus.client.Collector;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSinkUtil;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NaN;
import static java.lang.Double.parseDouble;

/**
 * Generates response for HTTP server from the received events Siddhi-Prometheus-sink passThrough mode.
*/
class ResponseGenerator {
    private static final Logger log = Logger.getLogger(ResponseGenerator.class);
    private String metricName;
    private Collector.Type metricType;
    private String valueAttribute;
    private Map<Integer, Map<String, Object>> eventMap = new LinkedHashMap<>();
    private Map<Integer, List<Map<String, Object>>> eventChunkMap = new LinkedHashMap<>();
    private List<Map<String, Object>> eventChunk = new ArrayList<>();     //For Histogram and Summary metrics
    private String metricHelp;

    void setMetricProperties(String metricName, Collector.Type metricType, String metricHelp,
                             String valueAttribute) {
        this.metricName = metricName;
        this.metricType = metricType;
        this.metricHelp = metricHelp;
        this.valueAttribute = valueAttribute;
    }

    String generateResponseBody() {
        StringBuilder builder = new StringBuilder();
        if (metricType != null) {
            if (metricType.equals(Collector.Type.COUNTER) || metricType.equals(Collector.Type.GAUGE)) {
                if (!eventMap.isEmpty()) {
                    for (Map.Entry<Integer, Map<String, Object>> entry : eventMap.entrySet()) {
                        builder.append(writeResponse(PrometheusSinkUtil.cloneMap(entry.getValue())));
                    }
                }
            } else if (metricType.equals(Collector.Type.HISTOGRAM) || metricType.equals(Collector.Type.SUMMARY)) {
                if (!eventChunkMap.isEmpty()) {
                    for (Map.Entry<Integer, List<Map<String, Object>>> entry : eventChunkMap.entrySet()) {
                        entry.getValue().forEach(event -> builder.append(writeResponse(
                                PrometheusSinkUtil.cloneMap(event))));
                    }
                }
            }
        }
        return builder.toString();
    }

    private String writeResponse(Map<String, Object> inputEvent) {
        StringBuilder builder = new StringBuilder(PrometheusConstants.EMPTY_STRING);
        inputEvent.remove(PrometheusConstants.MAP_NAME);
        inputEvent.remove(PrometheusConstants.MAP_TYPE);
        inputEvent.remove(PrometheusConstants.MAP_HELP);
        String subType = PrometheusConstants.EMPTY_STRING;
        if (inputEvent.containsKey(PrometheusConstants.MAP_SAMPLE_SUBTYPE)) {
            subType = inputEvent.get(PrometheusConstants.MAP_SAMPLE_SUBTYPE).toString();
            inputEvent.remove(PrometheusConstants.MAP_SAMPLE_SUBTYPE);
        }
        String sampleName = setSampleName(subType);
        double value = NaN;
        if (inputEvent.containsKey(valueAttribute)) {
            value = parseDouble(inputEvent.get(valueAttribute).toString());
            inputEvent.remove(valueAttribute);
        }
        builder.append(sampleName);
        if (subType.equals(PrometheusConstants.SUBTYPE_COUNT) ||
                subType.equals(PrometheusConstants.SUBTYPE_SUM)) {
            inputEvent.remove(PrometheusConstants.LE_KEY);
            inputEvent.remove(PrometheusConstants.QUANTILE_KEY);
        }
        if (inputEvent.size() > 0) {
            builder.append("{");
            for (Map.Entry<String, Object> entry : inputEvent.entrySet()) {
                builder.append(entry.getKey()).append("=\"");
                replaceEscapeCharacters((String) entry.getValue(), builder);
                builder.append("\",");
            }
            builder.append("}");
        }
        inputEvent.remove(PrometheusConstants.LE_KEY);
        inputEvent.remove(PrometheusConstants.QUANTILE_KEY);
        builder.append(PrometheusConstants.SPACE_STRING);
        builder.append(valueToString(value));
        builder.append(System.lineSeparator());
        return builder.toString();

    }

    private String setSampleName(String subType) {
        String sampleName = metricName;
        switch (subType) {
            case PrometheusConstants.SUBTYPE_NULL: {
                sampleName = metricName;
                break;
            }
            case PrometheusConstants.SUBTYPE_BUCKET: {
                sampleName += PrometheusConstants.BUCKET_POSTFIX;
                break;
            }
            case PrometheusConstants.SUBTYPE_COUNT: {
                sampleName += PrometheusConstants.COUNT_POSTFIX;
                break;
            }
            case PrometheusConstants.SUBTYPE_SUM: {
                sampleName += PrometheusConstants.SUM_POSTFIX;
                break;
            }
            default:
                //default will never be executed
        }
        return sampleName;
    }

    private String valueToString(double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return Double.toString(value);
    }

    private void replaceEscapeCharacters(String value, StringBuilder builder) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                default:
                    builder.append(character);
            }
        }
    }

    private void validateMetricType(Map<String, Object> metricMap) {
        String metricType = null;
        if (metricMap.containsKey(PrometheusConstants.MAP_TYPE)) {
            metricType = metricMap.get(PrometheusConstants.MAP_TYPE).toString();
        }
        if (metricType != null) {
            if (!metricType.equalsIgnoreCase(PrometheusSinkUtil.getMetricTypeString(this.metricType))) {
                log.error("The received metric type does not match with the stream definition of " +
                        "Prometheus sink in passThrough publish mode.", new SiddhiAppRuntimeException());
            }
        }
    }

    void analyseEvent(Map<String, Object> inputEvent, int eventHash) {
        validateMetricType(inputEvent);
        if (metricType != null) {
            switch (metricType) {
                case COUNTER:
                case GAUGE: {
                    if (eventMap.containsKey(eventHash)) {
                        eventMap.replace(eventHash, PrometheusSinkUtil.cloneMap(inputEvent));
                    } else {
                        eventMap.put(eventHash, PrometheusSinkUtil.cloneMap(inputEvent));
                    }
                    break;
                }
                case HISTOGRAM:
                case SUMMARY: {
                    if (inputEvent.containsKey(PrometheusConstants.MAP_SAMPLE_SUBTYPE)) {
                        String subType = inputEvent.get(PrometheusConstants.MAP_SAMPLE_SUBTYPE).toString();
                        switch (subType) {
                            case PrometheusConstants.SUBTYPE_BUCKET:
                            case PrometheusConstants.SUBTYPE_NULL:
                            case PrometheusConstants.SUBTYPE_COUNT: {
                                eventChunk.add(inputEvent);
                                break;
                            }
                            case PrometheusConstants.SUBTYPE_SUM: {
                                eventChunk.add(inputEvent);
                                if (eventChunkMap.containsKey(eventHash)) {
                                    eventChunkMap.replace(eventHash,
                                            PrometheusSinkUtil.cloneMapList(eventChunk));
                                } else {
                                    eventChunkMap.put(eventHash, PrometheusSinkUtil.cloneMapList(eventChunk));
                                }
                                eventChunk.clear();
                                break;
                            }
                            default:
                                //default will never be executed
                        }
                    }
                    break;
                }
                default:
                    //default will never be executed
            }
        }
    }

    String writeMetricProperties() {

        return "# HELP " + metricName +
                PrometheusConstants.SPACE_STRING + metricHelp +
                System.lineSeparator() +
                "# TYPE " + metricName + PrometheusConstants.SPACE_STRING +
                PrometheusSinkUtil.getMetricTypeString(metricType) +
                System.lineSeparator();
    }

    void clearMaps() {
        eventChunk.clear();
        eventChunkMap.clear();
        eventMap.clear();
    }
}
