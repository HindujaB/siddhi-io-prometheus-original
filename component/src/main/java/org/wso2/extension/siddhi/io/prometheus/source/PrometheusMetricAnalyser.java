package org.wso2.extension.siddhi.io.prometheus.source;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class analyses the response from http response, filter the metrics and generate maps according to
 * metric label-values.
 */
class PrometheusMetricAnalyser {

    private static final Logger log = Logger.getLogger(PrometheusMetricAnalyser.class);
    private String metricName;
    String metricJob;
    String metricInstance;
    private MetricType metricType;
    Map<String, String> metricGroupingKey;
    private String metricHelp;
    private SourceEventListener sourceEventListener;
    private List<String> lastValidSample = new ArrayList<>();

    PrometheusMetricAnalyser(String metricName, MetricType metricType, SourceEventListener sourceEventListener) {
        this.metricName = metricName;
        this.metricType = metricType;
        this.sourceEventListener = sourceEventListener;
    }

    void analyseMetrics(List<String> metricSamples, String targetURL) {

        String errorMessage = "Metric cannot be found inside the http response from " + targetURL + ".";

        int index = -1;
        for (int i = 0; i < metricSamples.size(); i++) {
            if ((metricSamples.get(i)).startsWith("# HELP " + metricName + " ")) {
                index = i;
            }
        }
        if (index == -1) {
            log.error(errorMessage);
            throw new PrometheusSourceException(errorMessage);
        } else {
            assignHelpString(metricSamples, index);
            if (!checkMetricType(metricSamples, index)) {
                log.error(errorMessage + " Metric type mismatching.");
                throw new PrometheusSourceException(errorMessage);
            } else {
                List<String> retrievedMetrics = metricSamples.stream().filter(
                        response -> response.startsWith(metricName)).collect(Collectors.toList());
                List<String> filteredMetrics = (List<String>) ((ArrayList) retrievedMetrics).clone();
                if ((!metricJob.equals(PrometheusConstants.EMPTY_STRING) ||
                        !metricInstance.equals(PrometheusConstants.EMPTY_STRING) || !metricGroupingKey.isEmpty())) {
                    for (String sampleSingleLine : retrievedMetrics) {
                        Map<String, String> labelPairMap = filterMetric(sampleSingleLine);
                        if (!(metricJob.equals(PrometheusConstants.EMPTY_STRING))) {
                            String job = labelPairMap.get("job");
                            if (job == null || !job.equalsIgnoreCase(metricJob)) {
                                filteredMetrics.remove(sampleSingleLine);
                                continue;
                            }
                        }
                        if (!(metricInstance.equals(PrometheusConstants.EMPTY_STRING))) {
                            String instance = labelPairMap.get("instance");
                            if (instance == null || !instance.equalsIgnoreCase(metricInstance)) {
                                filteredMetrics.remove(sampleSingleLine);
                                continue;
                            }
                        }
                        if (metricGroupingKey != null) {
                            for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                                String value = labelPairMap.get(entry.getKey());
                                if (value != null) {
                                    if (!value.equalsIgnoreCase(entry.getValue())) {
                                        filteredMetrics.remove(sampleSingleLine);
                                        break;
                                    }
                                } else {
                                    //if the grouping key not found in the metric,
                                    filteredMetrics.remove(sampleSingleLine);
                                    break;
                                }
                            }
                        }
                    }
                    if (filteredMetrics.isEmpty()) {
                        log.error(errorMessage + " Mismatching metric job, instance or grouping key.");
                        throw new PrometheusSourceException(errorMessage);
                    }
                }
                lastValidSample.addAll(filteredMetrics);
                generateMaps(filteredMetrics);
            }
        }
    }

    private void assignHelpString(List<String> metricSamples, int index) {
        String[] metricHelpArray = metricSamples.get(index).split(" ", 4);
        this.metricHelp = metricHelpArray[metricHelpArray.length - 1];

    }

    private void generateMaps(List<String> retrievedMetrics) {
        List<Map<String,Object>> mapList = new ArrayList<>();

        Map<String, String> labelValues = setIdealSample(retrievedMetrics.get(0));

        for (String sampleSingleLine : retrievedMetrics) {
            Map<String, Object> metricMap = new LinkedHashMap<>();
            metricMap.put(PrometheusConstants.MAP_NAME, metricName);
            metricMap.put(PrometheusConstants.MAP_TYPE, MetricType.getMetricTypeString(metricType));
            metricMap.put(PrometheusConstants.MAP_HELP, metricHelp);

            String sampleName = sampleSingleLine.substring(0, sampleSingleLine.indexOf("{"));
            Double value = Double.parseDouble(sampleSingleLine.substring(sampleSingleLine.indexOf("}") + 1));
            Map<String, String> labelValueMap = filterMetric(sampleSingleLine);
            if (sampleName.equals(metricName)) {
                metricMap.put(PrometheusConstants.MAP_SAMPLE_SUBTYPE, PrometheusConstants.SUBTYPE_NULL);
            }
            if (sampleName.equals(metricName + PrometheusConstants.BUCKET_POSTFIX)) {
                metricMap.put(PrometheusConstants.MAP_SAMPLE_SUBTYPE, PrometheusConstants.SUBTYPE_BUCKET);
            }
            if (sampleName.equals(metricName + PrometheusConstants.COUNT_POSTFIX)) {
                metricMap.put(PrometheusConstants.MAP_SAMPLE_SUBTYPE, PrometheusConstants.SUBTYPE_COUNT);
                addLeAndQuantileKeys(labelValueMap);
            }
            if (sampleName.equals(metricName + PrometheusConstants.SUM_POSTFIX)) {
                metricMap.put(PrometheusConstants.MAP_SAMPLE_SUBTYPE, PrometheusConstants.SUBTYPE_SUM);
                addLeAndQuantileKeys(labelValueMap);
            }
            labelValueMap.remove(PrometheusConstants.METRIC_JOB);
            labelValueMap.remove(PrometheusConstants.METRIC_INSTANCE);
            if (!metricGroupingKey.isEmpty()) {
                for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                    labelValueMap.remove(entry.getKey());
                }
            }
            MapDifference<String, String> mapDifference = Maps.difference(labelValues, labelValueMap);
            Map<String, MapDifference.ValueDifference<String>> valueDifferenceMap = mapDifference
                    .entriesDiffering();
            if (valueDifferenceMap.size() != 0) {
                if(valueDifferenceMap.containsKey(PrometheusConstants.QUANTILE_KEY) ||
                        valueDifferenceMap.containsKey(PrometheusConstants.LE_KEY)){
                    valueDifferenceMap.remove(PrometheusConstants.QUANTILE_KEY);
                    valueDifferenceMap.remove(PrometheusConstants.LE_KEY);
                }
            }
            if (!valueDifferenceMap.isEmpty()) {
                handleEvent(mapList);
                mapList.clear();
//                mapList.add(generateMap(metricLabels, metricValue, count, sum, quantiles, buckets));
                labelValues = setIdealSample(sampleSingleLine);
            }
            for (Map.Entry<String, String> entry : labelValueMap.entrySet()) {
                metricMap.put(entry.getKey(), entry.getValue());
            }
            metricMap.put(PrometheusConstants.MAP_SAMPLE_VALUE, value);
            mapList.add(metricMap);
            if (retrievedMetrics.indexOf(sampleSingleLine) == retrievedMetrics.size() -1) {
                handleEvent(mapList);
            }
        }
    }

    private Map<String, String> setIdealSample(String sample) {
        Map<String, String> idealSample = filterMetric(sample);
        idealSample.remove("job");
        idealSample.remove("instance");
        if (metricGroupingKey != null) {
            for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                idealSample.remove(entry.getKey());
            }
        }
        idealSample.remove("quantile");
        idealSample.remove("le");
        return idealSample;
    }

    private void addLeAndQuantileKeys(Map<String, String> labelValueMap) {
        switch (metricType) {
            case HISTOGRAM:
                labelValueMap.put(PrometheusConstants.LE_KEY, PrometheusConstants.EMPTY_STRING);
                break;
            case SUMMARY:
                labelValueMap.put(PrometheusConstants.QUANTILE_KEY, PrometheusConstants.EMPTY_STRING);
                break;
            default:
                //default will never be executed.
        }
    }

    private void handleEvent(List<Map<String, Object>> eventMapList) {
        sourceEventListener.onEvent(eventMapList.toArray(), null);
    }


    private Map<String, String> filterMetric(String metricSample) {
        String[] labelList = metricSample.substring(metricSample.indexOf("{") + 1, metricSample.indexOf("}"))
                .split(",");
        Map<String, String> labelMap = new LinkedHashMap<>();
        Arrays.stream(labelList).forEach(labelEntry -> {
            String[] entry = labelEntry.split("=");
            if (entry.length == 2) {
                String label = entry[0];
                String value = entry[1].substring(1, entry[1].length() - 1);
                labelMap.put(label, value);
            }
        });
        return labelMap;
    }

    private boolean checkMetricType(List<String> metricSamples, int index) {
        String[] metricTypeArray = metricSamples.get(index + 1).split(" ", 4);
        String metricTypeResponse = metricTypeArray[metricTypeArray.length - 1];
        return metricTypeResponse.equalsIgnoreCase(MetricType.getMetricTypeString(metricType));
    }

    List<String> getLastValidSamples() {
        return this.lastValidSample;
    }

    private static byte[] toByteArray(Object object) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream)) {
            objectOutputStream.writeObject(object);
            objectOutputStream.flush();
            bytes = byteStream.toByteArray();
        }
        return bytes;
    }
}
