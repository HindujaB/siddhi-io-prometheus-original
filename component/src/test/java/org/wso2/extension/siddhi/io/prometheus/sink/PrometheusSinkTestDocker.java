package org.wso2.extension.siddhi.io.prometheus.sink;

import org.apache.log4j.Logger;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.input.InputHandler;
import org.wso2.siddhi.core.stream.output.StreamCallback;
import org.wso2.siddhi.core.util.SiddhiTestHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PrometheusSinkTestDocker {

    private static final Logger log = Logger.getLogger(PrometheusSinkTestDocker.class);
    private static String pushgatewayURL;
    private static String serverURL;
    private static String buckets;
    private static String quantiles;
    private static ExecutorService executorService;
    private AtomicInteger eventCount = new AtomicInteger(0);
    private AtomicBoolean eventArrived = new AtomicBoolean(false);
    private List<Object[]> createdEvents = new ArrayList<>();

    @BeforeClass
    public static void startTest() {
        executorService = Executors.newFixedThreadPool(5);
        log.info("== Prometheus connection tests started ==");
        pushgatewayURL = "http://localhost:9091";
        serverURL = "http://localhost:9090";
        buckets = "2, 4, 6, 8";
        quantiles = "0.4,0.65,0.85";
    }

    @AfterClass
    public static void shutdown() throws InterruptedException {
        while (!executorService.isShutdown() || !executorService.isTerminated()) {
            executorService.shutdown();
        }
        Thread.sleep(100);
        log.info("== Prometheus connection tests completed ==");
    }

    @BeforeMethod
    public void beforeTest() {
        eventCount.set(0);
        eventArrived.set(false);
    }

    public void getMetrics(String metricName) {
        String urlString = "http://localhost:9090/api/v1/query?query=";

        urlString += metricName;

        StringBuilder out = new StringBuilder();

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            StringBuilder response = new StringBuilder();
            conn.connect();

            if (conn.getResponseCode() != 200) {
                Assert.fail("Http error: " + conn.getResponseCode() + "\n" + conn.getResponseMessage());
            } else {
                String inputLine;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }
                reader.close();
                JSONObject queryResult = new JSONObject(response.toString());
                JSONArray results = queryResult.getJSONObject("data").getJSONArray("result");
                for (int i = 0; i < results.length(); i++) {
                    Object name = results.getJSONObject(i).getJSONObject("metric").get("__name__");
                    Object job = results.getJSONObject(i).getJSONObject("metric").get("job");
                    Object symbol = results.getJSONObject(i).getJSONObject("metric").get("symbol");
                    Object price = results.getJSONObject(i).getJSONObject("metric").get("price");
                    Object value = results.getJSONObject(i).getJSONArray("value").get(1);

                    createdEvents.add(new Object[]{symbol, Integer.parseInt(value.toString()),
                            Double.parseDouble(price.toString())});
                }
            }
            conn.disconnect();

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }


    /**
     * test for Prometheus sink with keyvalue mapping.
     *
     * @throws InterruptedException
     */
    @Test
    public void prometheusSinkTest() throws InterruptedException {

        SiddhiManager siddhiManager = new SiddhiManager();
        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Sink test with mandatory fields");
        log.info("----------------------------------------------------------------------------------");
        String inputStream = "@App:name('TestSiddhiApp')" +
                "                        \"define stream InputStream (symbol String, value int, price double);";
        String sinkStream = "@sink(type='prometheus'," +
                "job='sinkTest'," +
                "publish.mode='pushgateway'," +
                "metric.type='counter'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SinkMapTestStream (symbol String, value int, price double);";
        String query = (
                "@info(name = 'query') "
                        + "from InputStream "
                        + "select *"
                        + "insert into SinkMapTestStream;"
        );
        StreamCallback streamCallback = new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                for (Event event : events) {
                    eventCount.getAndIncrement();
                    eventArrived.set(true);
                }
            }
        };

        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(inputStream + sinkStream + query);
        siddhiAppRuntime.addCallback("SinkMapTestStream", streamCallback);

        siddhiAppRuntime.start();

        List<Object[]> inputEvents = new ArrayList<>();
        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("InputStream");
        Object[] inputEvent1 = new Object[]{"WSO2", 100, 78.8};
        Object[] inputEvent2 = new Object[]{"IBM", 125, 65.32};
        inputHandler.send(inputEvent1);
        inputHandler.send(inputEvent2);
        inputEvents.add(inputEvent1);
        inputEvents.add(inputEvent2);
        Assert.assertTrue(eventArrived.get());
        Thread.sleep(1000);
        getMetrics("SinkMapTestStream");

        SiddhiTestHelper.isEventsMatch(inputEvents, createdEvents);
        Assert.assertEquals(eventCount.get(), 2);
        siddhiAppRuntime.shutdown();
    }

}
