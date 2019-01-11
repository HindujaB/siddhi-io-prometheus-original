package org.wso2.extension.siddhi.io.prometheus;
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

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import org.apache.log4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.io.prometheus.source.PrometheusSourceTest;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.output.StreamCallback;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PipelineTestcase {
    private static final Logger log = Logger.getLogger(PrometheusSourceTest.class);
    private static String targetURL;
    private static String serverURL;
    private static HTTPServer server;
    private AtomicInteger eventCount = new AtomicInteger(0);
    private AtomicBoolean eventArrived = new AtomicBoolean(false);
    private Event currentEvent;
    private Map<String, ArrayList<Object[]>> eventMap = new HashMap<>();
    private ArrayList<Object[]> receivedEvents = new ArrayList<>();

    @BeforeClass
    public void startTest() {
        String serverPort = System.getenv("SERVER_PORT");
        String passThroughServerPort = System.getenv("PASSTHROUGH_SERVER_PORT");
        String host = System.getenv("HOST_NAME");
        log.info("== Prometheus source tests started ==");
        targetURL = "http://" + host + ":" + serverPort + "/metrics";
        serverURL = "http://" + host + ":" + passThroughServerPort + "/metrics";
    }

    private void initializeMetrics() {
        CollectorRegistry registry = new CollectorRegistry();
        buildMetrics(registry);
//        Map<String,String> groupingKey = new HashMap<>();
//        groupingKey.put("key1","value1");
//        groupingKey.put("key2","value2");
        try {
            HTTPServer server = new HTTPServer(new InetSocketAddress(9080), registry);
//            PushGateway pushGateway = new PushGateway(new URL(targetURL));
//            pushGateway.push(registry, "testJob", groupingKey);
        } catch (IOException e) {
            if (!(e instanceof BindException && e.getMessage().equals("Address already in use"))) {
                log.error("Unable to establish connection ");
            }
        }
    }

    private void buildMetrics(CollectorRegistry registry) {
        Counter counter = Counter.build()
                .name("counter_test")
                .help("unit test - for counter metric")
                .labelNames("symbol", "price")
                .register(registry);
        counter.labels("WSO2", "78.8").inc(100);
        counter.labels("IBM", "65.32").inc(125);

        Gauge gauge = Gauge.build()
                .name("gauge_test")
                .help("unit test - for gauge metric")
                .labelNames("symbol", "price")
                .register(registry);
        gauge.labels("WSO2", "78.8").inc(100);
        gauge.labels("IBM", "65.32").inc(125);

        Histogram histogram = Histogram.build()
                .name("histogram_test")
                .help("unit test - for histogram metric")
                .labelNames("symbol", "price")
                .buckets(110, 130, 150)
                .register(registry);
        histogram.labels("WSO2", "78.8").observe(100);
        histogram.labels("IBM", "65.32").observe(125);


        Summary summary = Summary.build()
                .name("summary_test")
                .help("unit test - for summary metric")
                .labelNames("symbol", "price")
                .quantile(0.25, 0.001)
                .quantile(0.5, 0.001)
                .quantile(0.75, 0.001)
                .quantile(1, 0.0001)
                .register(registry);
        summary.labels("WSO2", "78.8").observe(100);
        summary.labels("IBM", "65.32").observe(125);
    }

    @BeforeMethod
    public void init() {
        eventCount.set(0);
        eventArrived.set(false);
        currentEvent = new Event();
    }

    @AfterClass
    public void endTest() {
        if (server != null) {
            server.stop();
            log.info("server stopped successfully.");
        }

    }

    /**
     * test for Prometheus source-sink pipelining with counter metric.
     *
     * @throws InterruptedException interrupted exception
     */
    @Test(sequential = true)
    public void prometheusSourceTest1() throws InterruptedException {
        initializeMetrics();
        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test for Prometheus source-sink pipelining with counter metric");
        log.info("----------------------------------------------------------------------------------");
        String metricType = "counter";
        String siddhiApp = "@App:name('TestSiddhiApp')";
        String outputStream1 = "@sink(type='prometheus'," +
                "job='sinkTest'," +
                "metric.help= 'test metric'," +
                "publish.mode='passThrough'," +
                "server.url='" + serverURL + "'," +
                "metric.type='" + metricType + "'," +
                "metric.name='counter_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream TestStream (metric_name String, metric_type String, help String, symbol String, price " +
                "String, subtype String, value double);";
        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '1'," +
                "scrape.timeout = '0.5'," +
                "metric.type='" + metricType + "'," +
                "metric.name='counter_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceMapTestStream (metric_name String, metric_type String," +
                " help String, symbol String, price String, subtype String, value double);";
        String query1 = (
                "@info(name = 'query1') "
                        + "from SourceMapTestStream "
                        + "select *"
                        + "insert into TestStream;"
        );

        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    log.info(event);
                    eventArrived.set(true);
                }
            }
        };
        SiddhiAppRuntime siddhiAppRuntime =
                siddhiManager.createSiddhiAppRuntime(siddhiApp + sourceStream + outputStream1 + query1);
        siddhiAppRuntime.addCallback("TestStream", streamCallback);
        StreamCallback insertionStreamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    currentEvent = event;
                    log.info(event);
                    eventArrived.set(true);
                    receivedEvents.add(event.getData());
                }
            }
        };

        siddhiAppRuntime.addCallback("SourceMapTestStream", insertionStreamCallback);
        siddhiAppRuntime.start();
        Thread.sleep(20000);
        siddhiAppRuntime.shutdown();
    }

    /**
     * test for Prometheus source-sink pipelining with counter metric.
     *
     * @throws InterruptedException interrupted exception
     */
    @Test(sequential = true)
    public void prometheusSourceTest2() throws InterruptedException {
        initializeMetrics();
        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test for Prometheus source-sink pipelining with gauge metric");
        log.info("----------------------------------------------------------------------------------");
        String metricType = "gauge";
        String siddhiApp = "@App:name('TestSiddhiApp')";
        String outputStream1 = "@sink(type='prometheus'," +
                "job='sinkTest'," +
                "metric.help= 'test metric'," +
                "publish.mode='passThrough'," +
                "server.url='" + serverURL + "'," +
                "metric.type='" + metricType + "'," +
                "metric.name='gauge_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream TestStream (metric_name String, metric_type String, help String, symbol String, price " +
                "String, subtype String, value double);";
        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '1'," +
                "scrape.timeout = '0.5'," +
                "metric.type='" + metricType + "'," +
                "metric.name='gauge_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceMapTestStream (metric_name String, metric_type String," +
                " help String, symbol String, price String, subtype String, value double);";
        String query1 = (
                "@info(name = 'query1') "
                        + "from SourceMapTestStream "
                        + "select *"
                        + "insert into TestStream;"
        );

        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    log.info(event);
                    eventArrived.set(true);
                }
            }
        };
        SiddhiAppRuntime siddhiAppRuntime =
                siddhiManager.createSiddhiAppRuntime(siddhiApp + sourceStream + outputStream1 + query1);
        siddhiAppRuntime.addCallback("TestStream", streamCallback);
        StreamCallback insertionStreamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    currentEvent = event;
                    log.info(event);
                    eventArrived.set(true);
                    receivedEvents.add(event.getData());
                }
            }
        };

        siddhiAppRuntime.addCallback("SourceMapTestStream", insertionStreamCallback);
        siddhiAppRuntime.start();
        Thread.sleep(20000);
        siddhiAppRuntime.shutdown();
    }

    /**
     * test for Prometheus source-sink pipelining with histogram metric.
     *
     * @throws InterruptedException interrupted exception
     */
    @Test(sequential = true)
    public void prometheusSourceTest3() throws InterruptedException {
        initializeMetrics();
        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test for Prometheus source-sink pipelining with histogram metric");
        log.info("----------------------------------------------------------------------------------");
        String metricType = "histogram";
        String siddhiApp = "@App:name('TestSiddhiApp')";
        String outputStream1 = "@sink(type='prometheus'," +
                "job='sinkTest'," +
                "metric.help= 'test metric'," +
                "publish.mode='passThrough'," +
                "server.url='" + serverURL + "'," +
                "metric.type='" + metricType + "'," +
                "metric.name='histogram_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream TestStream (metric_name String, metric_type String, help String, symbol String, price " +
                "String, subtype String, le String, value double);";
        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '1'," +
                "scrape.timeout = '0.5'," +
                "metric.type='" + metricType + "'," +
                "metric.name='histogram_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceMapTestStream (metric_name String, metric_type String," +
                " help String, symbol String, price String, subtype String, le String, value double);";
        String query1 = (
                "@info(name = 'query1') "
                        + "from SourceMapTestStream "
                        + "select *"
                        + "insert into TestStream;"
        );

        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    log.info(event);
                    eventArrived.set(true);
                }
            }
        };
        SiddhiAppRuntime siddhiAppRuntime =
                siddhiManager.createSiddhiAppRuntime(siddhiApp + sourceStream + outputStream1 + query1);
        siddhiAppRuntime.addCallback("TestStream", streamCallback);
        StreamCallback insertionStreamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    currentEvent = event;
                    log.info(event);
                    eventArrived.set(true);
                    receivedEvents.add(event.getData());
                }
            }
        };

        siddhiAppRuntime.addCallback("SourceMapTestStream", insertionStreamCallback);
        siddhiAppRuntime.start();
        Thread.sleep(20000);
        siddhiAppRuntime.shutdown();
    }

    /**
     * test for Prometheus source-sink pipelining with summary metric.
     *
     * @throws InterruptedException interrupted exception
     */
    @Test(sequential = true)
    public void prometheusSourceTest4() throws InterruptedException {
        initializeMetrics();
        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test for Prometheus source-sink pipelining with summary metric");
        log.info("----------------------------------------------------------------------------------");
        String metricType = "summary";
        String siddhiApp = "@App:name('TestSiddhiApp')";
        String outputStream1 = "@sink(type='prometheus'," +
                "job='sinkTest'," +
                "metric.help= 'test metric'," +
                "publish.mode='passThrough'," +
                "server.url='" + serverURL + "'," +
                "metric.type='" + metricType + "'," +
                "metric.name='summary_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream TestStream (metric_name String, metric_type String, help String, symbol String, price " +
                "String, subtype String, quantile String, value double);";
        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '1'," +
                "scrape.timeout = '0.5'," +
                "metric.type='" + metricType + "'," +
                "metric.name='summary_test'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceMapTestStream (metric_name String, metric_type String," +
                " help String, symbol String, price String, subtype String, quantile String, value double);";
        String query1 = (
                "@info(name = 'query1') "
                        + "from SourceMapTestStream "
                        + "select *"
                        + "insert into TestStream;"
        );

        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    log.info(event);
                    eventArrived.set(true);
                }
            }
        };
        SiddhiAppRuntime siddhiAppRuntime =
                siddhiManager.createSiddhiAppRuntime(siddhiApp + sourceStream + outputStream1 + query1);
        siddhiAppRuntime.addCallback("TestStream", streamCallback);
        StreamCallback insertionStreamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    currentEvent = event;
                    log.info(event);
                    eventArrived.set(true);
                    receivedEvents.add(event.getData());
                }
            }
        };

        siddhiAppRuntime.addCallback("SourceMapTestStream", insertionStreamCallback);
        siddhiAppRuntime.start();
        Thread.sleep(20000);
        siddhiAppRuntime.shutdown();
    }

    /**
     * test for Prometheus source-sink pipelining with summary metric.
     *
     * @throws InterruptedException interrupted exception
     */
    @Test(sequential = true)
    public void prometheusSourceTest5() throws InterruptedException {
        initializeMetrics();
        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test for Prometheus source-sink pipelining with summary metric");
        log.info("----------------------------------------------------------------------------------");
        String app = "@App:name(\"TestSiddhiApp3\")\n" +
                "@App:description(\"Description of the plan\")\n" +
                "\n" +
                "\n" +
                "@source(type = 'prometheus', target.url = 'http://localhost:9080', scheme = 'http', " +
                "scrape.interval = '2', scrape.timeout = '2', metric.type = 'histogram', " +
                "metric.name = 'test_histogram', \n" +
                "\t@map(type = 'keyvalue'))\n" +
                "Define stream SourceMapTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);\n" +
                " \n" +
                "@sink(type='prometheus' ,publish.mode = 'passThrough', server.url= 'http://localhost:9096/metrics'," +
                " metric.type='histogram',value.attribute='marks', metric.name='test_histogram_reproduced', @map" +
                "(type='keyvalue'))\n" +
                "define stream SinkStream(metric_name String, metric_type String, help String, name String, age " +
                "String, subtype String, le String, marks double);\n" +
                " \n" +
                " \n" +
                "@info(name = 'query') \n" +
                "from SourceMapTestStream\n" +
                "select metric_name, metric_type, help, name, age, subtype,le,value as marks   \n" +
                "insert into SinkStream;";


        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    log.info(event);
                    eventArrived.set(true);
                }
            }
        };
        SiddhiAppRuntime siddhiAppRuntime =
                siddhiManager.createSiddhiAppRuntime(app);
        siddhiAppRuntime.addCallback("SinkStream", streamCallback);
        StreamCallback insertionStreamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    currentEvent = event;
                    log.info(event);
                    eventArrived.set(true);
                    receivedEvents.add(event.getData());
                }
            }
        };

        siddhiAppRuntime.addCallback("SourceMapTestStream", insertionStreamCallback);
        siddhiAppRuntime.start();
        Thread.sleep(20000);
        siddhiAppRuntime.shutdown();
    }
}
