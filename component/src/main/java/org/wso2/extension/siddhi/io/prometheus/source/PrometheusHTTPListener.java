/*
 *  Copyright (c) 2017 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.extension.siddhi.io.prometheus.source;

import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.Http2PushPromise;

/**
 * {@code PrometheusHTTPListener} Handle the HTTP listener.
 */
public class PrometheusHTTPListener implements HttpConnectorListener {
    private HTTPCarbonMessage httpMessage;
//    private CountDownLatch latch;

    public PrometheusHTTPListener() {
        //this.latch = latch;
    }

    @Override
    public void onMessage(HTTPCarbonMessage httpMessage) {
        this.httpMessage = httpMessage;
        String statusCode = Integer.toString(httpMessage.getNettyHttpResponse().status().code());
        //latch.countDown();
    }

    @Override
    public void onError(Throwable throwable) {
//        latch.countDown();
    }

    @Override
    public void onPushPromise(Http2PushPromise pushPromise) {

    }

    public HTTPCarbonMessage getHttpResponseMessage() {
        return httpMessage;
    }

}
