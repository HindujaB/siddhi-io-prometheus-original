package org.wso2.extension.siddhi.io.prometheus.sink.util;
/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import io.prometheus.client.Collector;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSinkUtil;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.listener.ServerBootstrapConfiguration;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Double.parseDouble;

/**
 * HTTP server for Siddhi-Prometheus-sink passThrough mode.
 */
public class PrometheusPassThroughServer {
    private static final Logger log = Logger.getLogger(PrometheusPassThroughServer.class);
    private static List<String> recordedMetricsList = new ArrayList<>();
    private URL serverURL;
    private ServerConnector serverConnector;
    private ResponseGenerator responseGenerator;
    private PrometheusHTTPServerListener serverListener;

    public PrometheusPassThroughServer(URL serverURL) {
        this.serverURL = serverURL;
        serverListener = new PrometheusHTTPServerListener();
    }

    public void initiateResponseGenerator(String metricName, Collector.Type metricType, String metricHelp,
                                          String valueAttribute) {
        responseGenerator = new ResponseGenerator();
        responseGenerator.metricName = metricName;
        responseGenerator.metricHelp = metricHelp;
        responseGenerator.metricType = metricType;
        responseGenerator.valueAttribute = valueAttribute;
    }

    public void start() {
        HttpWsConnectorFactory connectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = getListenerConfiguration(serverURL);
        serverConnector = connectorFactory
                .createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
//        serverListener.setPayload(responseGenerator.response);
        serverConnectorFuture.setHttpConnectorListener(serverListener);

    }

    public void publishResponse(Map<String, Object> inputEvent) {
        responseGenerator.generateResponseBody(inputEvent);
        serverListener.setPayload(responseGenerator.response);
    }

    public void stop() {
        if (serverConnector != null) {
            serverConnector.stop();
        }
    }

    /**
     * Set Listener Configuration from given url.
     *
     * @param listenerUrl the url of the server
     * @return listener configuration.
     */
    private static ListenerConfiguration getListenerConfiguration(URL listenerUrl) {
        //Decoding parameters
        String protocol;
        String host;
        int port;
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        protocol = listenerUrl.getProtocol();
        host = listenerUrl.getHost();
        port = (listenerUrl.getPort());
        if (PrometheusConstants.HTTP_SCHEME.equalsIgnoreCase(protocol)) {
            listenerConfiguration = new ListenerConfiguration(PrometheusConstants.HTTP_SCHEME, host, port);
            listenerConfiguration.setId(host + PrometheusConstants.VALUE_SEPARATOR + port);
            listenerConfiguration.setScheme(protocol);
            listenerConfiguration.setVersion(String.valueOf(Constants.HTTP_2_0));
//                    listenerConfiguration.setMessageProcessorId(sourceConfigReader
//                            .readConfig(HttpConstants.MESSAGE_PROCESSOR_ID,
//                              HttpConstants.MESSAGE_PROCESSOR_ID_VALUE));
        } else {
            log.error("Invalid scheme found in the serverURL for passThrough mode of Prometheus sink.");
        }

        return listenerConfiguration;
    }

    private static void writeMetricProperties(String metricName, Collector.Type metricType, String metricHelp,
                                              StringBuilder builder) {
        if (!recordedMetricsList.contains(metricName)) {
            recordedMetricsList.add(metricName);
            builder.append("# HELP ").append(metricName);
            builder.append(PrometheusConstants.SPACE_STRING).append(metricHelp);
            builder.append(System.lineSeparator());
            builder.append("# TYPE ").append(metricName).append(PrometheusConstants.SPACE_STRING);
            builder.append(PrometheusSinkUtil.getMetricTypeString(metricType));
            builder.append(System.lineSeparator());
        }
    }

    /**
     * Generates response for HTTP server from the received events Siddhi-Prometheus-sink passThrough mode.
     */
    static class ResponseGenerator {
        private String metricName;
        private Collector.Type metricType;
        private String metricHelp;
        private String response = PrometheusConstants.EMPTY_STRING;
        private String valueAttribute;

        void generateResponseBody(Map<String, Object> inputEvent) {
            validateAndOverrideMetricProperties(inputEvent);
            StringBuilder builder = new StringBuilder(response);
            PrometheusPassThroughServer.writeMetricProperties(metricName, metricType, metricHelp, builder);
            inputEvent.remove(PrometheusConstants.MAP_NAME);
            inputEvent.remove(PrometheusConstants.MAP_TYPE);
            inputEvent.remove(PrometheusConstants.MAP_HELP);
            String subType = PrometheusConstants.EMPTY_STRING;
            if (inputEvent.containsKey(PrometheusConstants.MAP_SAMPLE_SUBTYPE)) {
                subType = inputEvent.get(PrometheusConstants.MAP_SAMPLE_SUBTYPE).toString();
                inputEvent.remove(PrometheusConstants.MAP_SAMPLE_SUBTYPE);
            }
            String sampleName = setSampleName(subType);
            double value = 0.0;
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
            builder.append(PrometheusConstants.SPACE_STRING);
            builder.append(valueToString(value));
            builder.append(System.lineSeparator());
            response = builder.toString();
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

        private void validateAndOverrideMetricProperties(Map<String, Object> metricMap) {
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

    }
}

