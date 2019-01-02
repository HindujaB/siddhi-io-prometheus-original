package org.wso2.extension.siddhi.io.prometheus.source;

import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.stream.input.InputHandler;

public class ValidationTestcase {
    private static final Logger log = Logger.getLogger(org.wso2.extension.siddhi.io.prometheus.source.
            ValidationTestcase.class);
    private static String targetURL;
    private static String buckets;
    private static String quantiles;

    @BeforeClass
    public static void startTest() {
        log.info("== Prometheus source validation tests started ==");
        targetURL = "http://localhost:9080";
        buckets = "2, 4, 6, 8";
        quantiles = "0.4,0.65,0.85";
    }

    @AfterClass
    public static void shutdown() throws InterruptedException {
        Thread.sleep(100);
        log.info("== Prometheus source validation tests completed ==");
    }


    private void startSiddhiApp(String streamDefinition) {
        SiddhiManager siddhiManager = new SiddhiManager();
        String siddhiApp = "@App:name('TestSiddhiApp')";
        String outputStream = " @sink(type='log', prefix='test')" +
                "define stream OutputStream (metric_name String, metric_type String, help String," +
                " name String, age String, subtype String, le String, value double);";
        String query = (
                "@info(name = 'query') "
                        + "from SourceTestStream "
                        + "select * "
                        + "insert into OutputStream;"
        );
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(siddhiApp + streamDefinition +
                outputStream + query);
        InputHandler inputStream = siddhiAppRuntime.getInputHandler("SourceTestStream");
        siddhiAppRuntime.start();
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "The field \'scheme\' contains unsupported value in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " in (.*)")
    public void prometheusValidationTest1() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with invalid scheme");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'tcp'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "target URL field found empty but it is a Mandatory field of " +
                    "" + PrometheusConstants.PROMETHEUS_SOURCE + " in (.*)")
    public void prometheusValidationTest2() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with empty target URL");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'\', " +
                "scheme = 'http'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "The value of fields scrape interval or scrape timeout of " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " cannot be negative in (.*)")
    public void prometheusValidationTest3() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with negative value for scrape interval");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '-3'," +
                "scrape.timeout = '2'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "Please provide user name and password in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " with the stream ().* in Siddhi app (.*)")
    public void prometheusValidationTest4() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with either empty user name or password");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "username = \"\"," +
                "password = 'abc'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "Client trustStore file path or password are empty while " +
                    "default scheme is 'https'. Please provide client trustStore file path and password in (.*)")
    public void prometheusValidationTest5() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test without client trust store file or password in https scheme");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'https://localhost:9080\', " +
                "scheme = 'https'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "client.truststore.file = \"\"," +
                "client.truststore.password = 'abc'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "The Prometheus source associated with stream (.*) " +
                    "contains an invalid value for target URL")
    public void prometheusValidationTest6() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with invalid target URL ");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'hs:local-host:9080\', " +
                "scheme = 'https'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "client.truststore.file = \"\"," +
                "client.truststore.password = 'abc'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "The provided scheme and the scheme of target URL are " +
                    "not matching in Prometheus source with stream (.*)")
    public void prometheusValidationTest7() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with non matching values in scheme and target URL ");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'https://localhost:9080\', " +
                "scheme = 'http'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = "Invalid header format. Please include as " +
                    "'key1:value1','key2:value2',..")
    public void prometheusValidationTest8() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with incorrect format of key-value input ");
        log.info("----------------------------------------------------------------------------------");

        String headers = "header1-value1,header2-value2";
        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '3'," +
                "headers = \'" + headers + "\'," +
                "scrape.timeout = '2'," +
                "metric.type='histogram'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        try {
            startSiddhiApp(sourceStream);
            Assert.fail("Exception expected");
        } catch (SiddhiAppCreationException e) {
            throw new SiddhiAppCreationException(e.getMessageWithOutContext());
        }
    }
}
