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

package org.wso2.extension.siddhi.io.prometheus.sink.util;

import io.prometheus.client.Collector;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
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

/**
 * HTTP server for Siddhi-Prometheus-sink passThrough mode.
 */
public class PrometheusPassThroughServer {
    private static final Logger log = Logger.getLogger(PrometheusPassThroughServer.class);
    private static List<String> recordedMetricsList = new ArrayList<>();
    private static String payloadStack = PrometheusConstants.EMPTY_STRING;
    private URL serverURL;
    private HttpWsConnectorFactory connectorFactory;
    private static ResponseGenerator responseGenerator = new ResponseGenerator();
    private PrometheusHTTPServerListener serverListener;

    public PrometheusPassThroughServer(URL serverURL, String metricName, Collector.Type metricType, String metricHelp
            , String valueAttribute) {
        this.serverURL = serverURL;
        this.serverListener = new PrometheusHTTPServerListener();
        responseGenerator.setMetricProperties(metricName, metricType, metricHelp, valueAttribute);
    }

    static String getResponsePayload() {
        return responseGenerator.writeMetricProperties() + responseGenerator.generateResponseBody();
    }

    public void start() {
        connectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = getListenerConfiguration(serverURL);
        ServerConnector serverConnector = connectorFactory
                .createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        serverConnectorFuture.setHttpConnectorListener(serverListener);
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            log.error("Thread Interrupted while sync ", e);
        }
    }

    public void analyseReceivedEvent(Map<String, Object> inputEvent, int eventHash) {
        responseGenerator.analyseEvent(inputEvent, eventHash);
    }

    public void stop() {
        serverListener.disconnect();
        try {
            connectorFactory.shutdown();
        } catch (InterruptedException e) {
            log.error(e, new SiddhiAppRuntimeException(e));
        }
        responseGenerator.clearMaps();
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
        } else {
            log.error("Invalid scheme found in the serverURL for passThrough mode of Prometheus sink.");
        }

        return listenerConfiguration;
    }
}

