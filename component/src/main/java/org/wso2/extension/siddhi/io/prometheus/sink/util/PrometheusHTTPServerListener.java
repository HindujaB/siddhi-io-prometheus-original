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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * HTTP connector listener for Siddhi-Prometheus-sink passThrough mode.
 */
public class PrometheusHTTPServerListener implements HttpConnectorListener {
    private static final Logger log = Logger.getLogger(PrometheusHTTPServerListener.class);
    private String payload = PrometheusConstants.EMPTY_STRING;
    private HTTPCarbonMessage httpResponse = generateResponseMessage();
    private final int contentBufferSize = 8192;
    private ByteBuf buffer = Unpooled.buffer(contentBufferSize);
    private HttpContent httpContent = new DefaultLastHttpContent(buffer);


    @Override
    public void onMessage(HTTPCarbonMessage httpRequest) {
        if (PrometheusConstants.HTTP_SCHEME.equals(httpRequest.getProperty(Constants.PROTOCOL))) {
            sendResponse(httpRequest);
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

    private void createResponsePayload(String responseValue, HTTPCarbonMessage httpResponse) {
        byte[] array;
        array = responseValue.getBytes(StandardCharsets.UTF_8);
        httpResponse.setHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(array.length));
        ByteBuffer byteBuffer1 = ByteBuffer.allocate(array.length);
        byteBuffer1.put(array);
        byteBuffer1.flip();
        httpResponse.addHttpContent(new DefaultLastHttpContent(Unpooled.wrappedBuffer(byteBuffer1)));
    }

    private void sendResponse(HTTPCarbonMessage httpRequest) {
        // Send the intended response message
        try {
            HttpResponseFuture responseFuture;
            String payload = PrometheusPassThroughServer.getResponsePayload();
            createResponsePayload(payload, httpResponse);
            responseFuture = httpRequest.respond(httpResponse);
            Throwable error = responseFuture.getStatus().getCause();
            if (error != null) {
                responseFuture.resetStatus();
                log.error("Error occurred while sending the response " + error.getMessage());
            }
        } catch (ServerConnectorException e) {
            log.error("Error occurred while processing message: " + e.getMessage());
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

}
