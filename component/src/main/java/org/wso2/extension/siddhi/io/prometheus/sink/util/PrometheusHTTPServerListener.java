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

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.log4j.Logger;
import org.wso2.carbon.messaging.Constants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpCarbonResponse;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;

import java.io.IOException;

/**
 * HTTP connector listener for Siddhi-Prometheus-sink passThrough mode.
 */
public class PrometheusHTTPServerListener implements HttpConnectorListener {
    private static final Logger log = Logger.getLogger(PrometheusHTTPServerListener.class);
    private String payload = PrometheusConstants.EMPTY_STRING;


    @Override
    public void onMessage(HTTPCarbonMessage httpRequest) {
        if (PrometheusConstants.HTTP_SCHEME.equals(httpRequest.getProperty(Constants.PROTOCOL))) {
            HTTPCarbonMessage httpResponse = generateResponseMessage();
            sendResponse(httpRequest, httpResponse);
        }
    }

    private HTTPCarbonMessage generateResponseMessage() {
        HTTPCarbonMessage httpResponse = null;
        try {
            httpResponse = new HttpCarbonResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK));
            httpResponse.setHeader(HttpHeaderNames.CONTENT_TYPE.toString(), PrometheusConstants.TEXT_PLAIN);
        } catch (Exception e) {
            log.error("Error occurred during message notification: " + e.getMessage());
        }
        return httpResponse;
    }

    private void sendResponse(HTTPCarbonMessage httpRequest, HTTPCarbonMessage httpResponse) {
        // Send the intended response message
        try {
            HttpResponseFuture responseFuture;
            responseFuture = httpRequest.respond(httpResponse);
            HttpMessageDataStreamer httpMessageDataStreamer = new HttpMessageDataStreamer(httpResponse);
            httpMessageDataStreamer.getOutputStream().write(payload.getBytes());
            httpMessageDataStreamer.getOutputStream().close();
            Throwable error = responseFuture.getStatus().getCause();
            if (error != null) {
                responseFuture.resetStatus();
                log.error("Error occurred while sending the response " + error.getMessage());
            }
        } catch (ServerConnectorException | IOException e) {
            log.error("Error occurred while processing message: " + e.getMessage());
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

    void setPayload(String payload) {
        this.payload = payload;
    }
}