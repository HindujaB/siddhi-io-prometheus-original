package org.wso2.extension.siddhi.io.prometheus.source;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.log4j.Logger;
import org.wso2.carbon.messaging.Header;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSourceUtil;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants.EMPTY_STRING;

/**
 *
 */
public class PrometheusScraper implements Runnable {
    private static final Logger log = Logger.getLogger(PrometheusScraper.class);
    private String targetURL;
    private double scrapeInterval;
    private double scrapeTimeout;
    private String scheme;
    private List<Header> headers;
    private String userName = EMPTY_STRING;
    private String password = EMPTY_STRING;
    private String clientStoreFile;
    private String clientStorePassword;
    private List<String> lastValidSamples;
    private PrometheusMetricAnalyser metricAnalyser;
    private boolean isPaused = false;
    private List<String> metricSamples;
    private SourceEventListener sourceEventListener;

    PrometheusScraper(String targetURL, String scheme, double scrapeInterval, double scrapeTimeout,
                      List<Header> headers, SourceEventListener sourceEventListener) {
        this.targetURL = targetURL;
        this.scheme = scheme;
        this.scrapeInterval = scrapeInterval;
        this.scrapeTimeout = scrapeTimeout;
        this.headers = headers;
        this.sourceEventListener = sourceEventListener;
    }

    void setMetricProperties(String metricName, MetricType metricType, String metricJob,
                             String metricInstance, Map<String, String> metricGroupingKey) {
        this.metricAnalyser = new PrometheusMetricAnalyser(metricName, metricType, sourceEventListener);
        metricAnalyser.metricJob = metricJob;
        metricAnalyser.metricInstance = metricInstance;
        metricAnalyser.metricGroupingKey = metricGroupingKey;
    }


    void setAuthorizationHeader(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    void setHttpsProperties(String clientStoreFile, String clientStorePassword) {
        if (scheme.equalsIgnoreCase(PrometheusConstants.HTTPS_SCHEME)) {
            this.clientStoreFile = clientStoreFile;
            this.clientStorePassword = clientStorePassword;
        }
    }

    private String encode(String userNamePassword) {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(userNamePassword.getBytes(StandardCharsets.UTF_8));
        ByteBuf encodedByteBuf = Base64.encode(byteBuf);
        return encodedByteBuf.toString(StandardCharsets.UTF_8);
    }

    private void retrieveMetricSamples() throws IOException {
        Map<String, String> urlProperties = PrometheusSourceUtil.getURLProperties(targetURL, scheme);
        SenderConfiguration senderConfiguration = PrometheusSourceUtil.getSenderConfigurations(urlProperties,
                clientStoreFile, clientStorePassword);
        if (scrapeTimeout != -1) {
            senderConfiguration.setSocketIdleTimeout((int) (scrapeTimeout * 1000));
        }
        if (!(EMPTY_STRING.equals(userName) || EMPTY_STRING.equals(password))) {
            String basicAuthHeader = "Basic " + encode(userName + ":" + password);
            headers.add(new Header(PrometheusConstants.AUTHORIZATION_HEADER, basicAuthHeader));
        }
        HttpWsConnectorFactory httpConnectorFactory = new DefaultHttpWsConnectorFactory();
        HttpClientConnector httpClientConnector = httpConnectorFactory.createHttpClientConnector(new HashMap<>(),
                senderConfiguration);
        metricSamples = sendRequest(httpClientConnector, urlProperties, headers);

        String errorMessage = PrometheusConstants.EMPTY_STRING;
        if (metricSamples == null) {
            errorMessage = "Error occurred while retrieving metrics. Error : Response is null.";
        } else if (metricSamples.isEmpty()) {
            errorMessage = "The target at" + targetURL + "returns an empty response";
        }
        if (!errorMessage.equals(PrometheusConstants.EMPTY_STRING)) {
            log.error(errorMessage);
            throw new SiddhiAppRuntimeException(errorMessage);
        } else {
            metricAnalyser.analyseMetrics(metricSamples, targetURL);
            this.lastValidSamples = metricAnalyser.getLastValidSamples();
        }
    }

    private static List<String> sendRequest(HttpClientConnector clientConnector, Map<String, String> urlProperties,
                                            List<Header> headerList) {
        List<String> responsePayload;
        CountDownLatch latch = new CountDownLatch(1);

        HttpMethod httpReqMethod = new HttpMethod(PrometheusConstants.DEFAULT_HTTP_METHOD);
        HTTPCarbonMessage carbonMessage = new HTTPCarbonMessage(new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                httpReqMethod, EMPTY_STRING));
        carbonMessage = generateCarbonMessage(headerList, urlProperties, carbonMessage);
        carbonMessage.completeMessage();
        HttpResponseFuture httpResponseFuture = clientConnector.send(carbonMessage);

        PrometheusHTTPClientListener httpListener = new PrometheusHTTPClientListener(latch);
        httpResponseFuture.setHttpConnectorListener(httpListener);
        try {
            latch.await(90,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.debug("Thread waiting time-out issue: " + e);
        }

        HTTPCarbonMessage response = httpListener.getHttpResponseMessage();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                new HttpMessageDataStreamer(response).getInputStream(), Charset.defaultCharset()));
        int statusCode = response.getNettyHttpResponse().status().code();
        if (statusCode == 200) {
            responsePayload = bufferedReader.lines().collect(Collectors.toList());
//            try {
//                streamer.getInputStream().close();
//            } catch (IOException e) {
//                log.error("Error while closing the stream " + e);
//            }
        } else {
            String errorMessage = "Error occurred while retrieving metrics. HTTP error code: " +
                    statusCode;
            log.error(errorMessage + " " + response.getNettyHttpResponse().status().toString());
            throw new SiddhiAppRuntimeException(errorMessage);
        }
        return responsePayload;
    }

    private static HTTPCarbonMessage generateCarbonMessage(List<Header> headers, Map<String, String> urlProperties,
                                                           HTTPCarbonMessage carbonMessage) {
        /*
         * set carbon message properties which is to be used in carbon transport.
         */
        // Set protocol type http or https
        carbonMessage.setProperty(Constants.PROTOCOL, urlProperties.get(Constants.PROTOCOL));
        // Set uri
        carbonMessage.setProperty(Constants.TO, urlProperties.get(Constants.TO));
        // set Host
        carbonMessage.setProperty(Constants.HTTP_HOST, urlProperties.get(Constants.HTTP_HOST));
        //set port
        carbonMessage.setProperty(Constants.HTTP_PORT, Integer.valueOf(urlProperties.get(Constants.HTTP_PORT)));
        // Set method
        carbonMessage.setProperty(Constants.HTTP_METHOD, PrometheusConstants.DEFAULT_HTTP_METHOD);
        //Set request URL
        carbonMessage.setProperty(Constants.REQUEST_URL, urlProperties.get(Constants.REQUEST_URL));
        HttpHeaders httpHeaders = carbonMessage.getHeaders();
        httpHeaders.set(Constants.HTTP_HOST, carbonMessage.getProperty(Constants.HTTP_HOST));
        /*
         *set request headers.
         */
        // Set user given Headers
        if (headers != null) {
            for (Header header : headers) {
                httpHeaders.set(header.getName(), header.getValue());
            }
        }
        // Set content type if content type is not included in headers
        httpHeaders.set(PrometheusConstants.HTTP_CONTENT_TYPE, PrometheusConstants.TEXT_PLAIN);

        //set method-type header
        httpHeaders.set(PrometheusConstants.HTTP_METHOD, PrometheusConstants.DEFAULT_HTTP_METHOD);
        return carbonMessage;
    }

    @Override
    public void run() {
        if (!isPaused) {
            try {
                retrieveMetricSamples();
                try {
                    Thread.sleep((long) scrapeInterval * 1000);
                } catch (InterruptedException e) {
                    log.error("Error while scraping from " + targetURL + ".", e);
                }
            } catch (IOException e) {
                throw new PrometheusSourceException(e);
            }
        }
    }

    void pause() {
        isPaused = true;
    }

    void resume() {
        isPaused = false;
    }

    List<String> getLastValidResponse() {
        return this.lastValidSamples;
    }

    void setLastValidResponse(List<String> lastValidResponse) {
        this.lastValidSamples = lastValidResponse;
    }

    void clearPrometheusScraper() {
        if(metricSamples != null) {
            metricSamples.clear();
        }
        if(lastValidSamples != null) {
            lastValidSamples.clear();
        }
    }
}
