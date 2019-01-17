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

package org.wso2.extension.siddhi.io.prometheus.source;

import org.apache.log4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.stream.input.InputHandler;

/**
 * Test cases for invalid source definitions.
 */
public class SourceValidationTestcase {
    private static final Logger log = Logger.getLogger(SourceValidationTestcase.class);
    private static String targetURL;
    private static final String ERROR_MESSAGE = "Error on \'(.*)\' @ Line: (.*). Position: (.*), near \'(.*)\'. ";


    @BeforeClass
    public static void startTest() {
        log.info("== Prometheus source validation tests started ==");
        targetURL = "http://localhost:9080";
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
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The field \'scheme\' contains unsupported value in " +
                    "(.*) of " + PrometheusConstants.PROMETHEUS_SOURCE)
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The target URL field found empty but it is a Mandatory" +
                    " field of " +
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The value of fields scrape interval or scrape timeout " +
                    "from " + PrometheusConstants.PROMETHEUS_SOURCE + " cannot be negative in (.*)")
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "Please provide user name and password in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " associated with the stream ().* in Siddhi app (.*)")
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "Client trustStore file path or password are empty " +
                    "while default scheme is 'https'. Please provide client trustStore file path and password in (.*)" +
                    " of (.*)")
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The Prometheus source associated with stream (.*) " +
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The provided scheme and the scheme of target URL are " +
                    "not matching in Prometheus source associated with stream (.*)")
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp =
                    ERROR_MESSAGE + "Invalid header format found in " + PrometheusConstants.PROMETHEUS_SOURCE + " " +
                    "associated with stream \'(.*)\'. Please include them as " +
                    "'key1:value1', 'key2:value2',..")
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
        startSiddhiApp(sourceStream);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class,
            expectedExceptionsMessageRegExp = ERROR_MESSAGE + "The \'metric.type\' field in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " associated with stream \'(.*)\' contains illegal value")
    public void prometheusValidationTest9() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();

        log.info("----------------------------------------------------------------------------------");
        log.info("Prometheus Source test with invalid metric type ");
        log.info("----------------------------------------------------------------------------------");

        String sourceStream = "@source(type='prometheus'," +
                "target.url=\'" + targetURL + "\', " +
                "scheme = 'http'," +
                "scrape.interval = '3'," +
                "scrape.timeout = '2'," +
                "metric.type='metric'," +
                "metric.name='test_histogram'," +
                "@map(type = 'keyvalue'))" +
                "Define stream SourceTestStream (metric_name String, metric_type String, help String, name String," +
                " age String, subtype String, le String, value double);";
        startSiddhiApp(sourceStream);
    }
}
