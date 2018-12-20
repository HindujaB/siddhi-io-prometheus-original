package org.wso2.extension.siddhi.io.prometheus.source;

import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.auth.BasicAuthRequestInterceptor;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PrometheusScraper implements Runnable {
    private static final Logger log = Logger.getLogger(PrometheusScraper.class);
    private String targetURL;
    private int scrapeInterval;
    private int scrapeTimeout;
    private String scheme;
    private Map<String, String> headers;
    private String userName = PrometheusConstants.EMPTY_STRING;
    private String password = PrometheusConstants.EMPTY_STRING;
    private String clientStoreFile;
    private String clientStorePassword;
    private List<String> lastValidSamples;
    private PrometheusMetricAnalyser metricAnalyser;
    private boolean isPaused = false;
    private SourceEventListener sourceEventListener;


    PrometheusScraper(String targetURL, String scheme, int scrapeInterval, int scrapeTimeout,
                      Map<String, String> headers, SourceEventListener sourceEventListener) {
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
        } else {
            String errorMessage = "The fields client truststore file and password in Prometheus source do not " +
                    "support for scheme http.";
            throw new PrometheusSourceException(errorMessage);
        }
    }

    static class HeaderInterceptor implements RequestInterceptor {
        private Map<String, String> headers;

        @Override
        public void apply(RequestTemplate template) {
            if (this.headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    template.header(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    interface PrometheusHttpInterface {
        @RequestLine("GET /")
        Response getMetrics();
    }

    private List<String> getMetricSamples() throws IOException {

        HeaderInterceptor headerInterceptor = new HeaderInterceptor();
        headerInterceptor.headers = headers;
        PrometheusHttpInterface prometheusInterface = Feign.builder()
                .requestInterceptor(headerInterceptor)
                .requestInterceptor(new BasicAuthRequestInterceptor(userName, password))
                .options(new Request.Options(scrapeTimeout, scrapeTimeout))
                .target(PrometheusHttpInterface.class, targetURL);
        List<String> metricSamples;
        Response metricResponse = prometheusInterface.getMetrics();
        if (metricResponse == null) {
            String errorMessage = "Error occurred while retrieving metrics. Error : Response is null.";
            log.error(errorMessage);
            throw new SiddhiAppRuntimeException(errorMessage);
        }
        if (metricResponse.status() == 200) {
            InputStream inputStream = metricResponse.body().asInputStream();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                metricSamples = br.lines().collect(Collectors.toList());
            }
        } else {
            String errorMessage = "Error occurred while retrieving metrics. HTTP error code: " +
                    metricResponse.status();
            log.error(errorMessage);
            throw new SiddhiAppRuntimeException(errorMessage);
        }
        return metricSamples;
    }


    @Override
    public void run() {
        if (!isPaused) {
            try {
                List<String> metricSamples = getMetricSamples();
                if (metricSamples.isEmpty()) {
                    throw new PrometheusSourceException("The target at" + targetURL + "returns an empty response");
                } else {
                    metricAnalyser.analyseMetrics(metricSamples);
                    this.lastValidSamples = metricAnalyser.getLastValidSamples();
                }
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


}
