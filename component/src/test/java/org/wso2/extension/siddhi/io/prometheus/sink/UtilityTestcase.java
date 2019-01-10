package org.wso2.extension.siddhi.io.prometheus.sink;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.prometheus.client.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.io.prometheus.sink.util.PrometheusPassThroughServer;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpCarbonRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants.EMPTY_STRING;

public class UtilityTestcase {
    private static final Logger LOG = LoggerFactory.getLogger(UtilityTestcase.class);

    private HttpClientConnector httpClientConnector;
    private ServerConnector serverConnector;
    private SenderConfiguration senderConfiguration;
    private HttpWsConnectorFactory connectorFactory;
    private URL serverURL;
    private PrometheusPassThroughServer server;
    private Map<String, Object>[] inputEvents;

    @BeforeClass
    public void setup() throws InterruptedException {
        try {
            serverURL = new URL("http://localhost:9080");
        } catch (MalformedURLException e) {
            LOG.error("malformed URL");
        }
        server  = new PrometheusPassThroughServer(serverURL);

        senderConfiguration = new SenderConfiguration(String.valueOf(serverURL.getPort()));
        senderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_2_0));
        senderConfiguration.setScheme(serverURL.getProtocol());



        Map<String, Object> event1 = new LinkedHashMap<>();
        event1.put("metric_name", "test_metric_change");
        event1.put("metric_type", "counter");
        event1.put("help", "help string");
        event1.put("subtype", "null");
        event1.put("name", "Hindu");
        event1.put("age", "23");
        event1.put("value", "2.3");

        Map<String, Object> event2 = new LinkedHashMap<>();
        event2.put("metric_name", "test_metric_change");
        event2.put("metric_type", "counter");
        event2.put("help", "help string");
        event2.put("subtype", "null");
        event2.put("name", "Nisha");
        event2.put("age", "19");
        event2.put("value", "1.9");

        inputEvents = new Map[] {event1, event2};
    }

    @Test
    public void testHttp2Post() {
        server.start();
        server.initiateResponseGenerator("test_metric", Collector.Type.COUNTER, "help string");
        server.publishResponse(inputEvents);
        LOG.info(server.toString());
        try {
            Thread.sleep(30000);
            server.stop();
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }
    }

    private HTTPCarbonMessage generateRequest(HttpMethod method) {

        HTTPCarbonMessage carbonMessage = new HttpCarbonRequest(new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                method, EMPTY_STRING));
            carbonMessage.setProperty(Constants.PROTOCOL, serverURL.getProtocol());
            // Set uri
            carbonMessage.setProperty(Constants.TO, serverURL.getFile());
            // set Host
            carbonMessage.setProperty(Constants.HTTP_HOST, serverURL.getHost());
            //set port
            carbonMessage.setProperty(Constants.HTTP_PORT, serverURL.getPort());
            // Set method
            carbonMessage.setProperty(Constants.HTTP_METHOD, PrometheusConstants.DEFAULT_HTTP_METHOD);
            //Set request URL
            carbonMessage.setProperty(Constants.REQUEST_URL, serverURL);
            return carbonMessage;
    }

    @AfterClass
    public void cleanUp() {
//        senderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_1_1));
//        httpClientConnector.close();
//        serverConnector.stop();
//        try {
//            connectorFactory.shutdown();
//        } catch (InterruptedException e) {
//            LOG.warn("Interrupted while waiting for HttpWsFactory to close");
//        }
    }

}
