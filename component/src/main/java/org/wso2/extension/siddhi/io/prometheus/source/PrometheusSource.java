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

import org.wso2.carbon.messaging.Header;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSourceUtil;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.SystemParameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.stream.input.source.Source;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.OptionHolder;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.exception.AttributeNotExistException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants.EMPTY_STRING;

/**
 * This is a sample class-level comment, explaining what the extension class does.
 */

/**
 * Annotation of Siddhi Extension.
 * <pre><code>
 * eg:-
 * {@literal @}Extension(
 * name = "The name of the extension",
 * namespace = "The namespace of the extension",
 * description = "The description of the extension (optional).",
 * //Source configurations
 * parameters = {
 * {@literal @}Parameter(name = "The name of the first parameter",
 *                               description= "The description of the first parameter",
 *                               type =  "Supported parameter types.
 *                                        eg:{DataType.STRING, DataType.INT, DataType.LONG etc}",
 *                               dynamic= "false
 *                                         (if parameter doesn't depend on each event then dynamic parameter is false.
 *                                         In Source, only use static parameter)",
 *                               optional= "true/false, defaultValue= if it is optional then assign a default value
 *                                          according to the type."),
 * {@literal @}Parameter(name = "The name of the second parameter",
 *                               description= "The description of the second parameter",
 *                               type =   "Supported parameter types.
 *                                         eg:{DataType.STRING, DataType.INT, DataType.LONG etc}",
 *                               dynamic= "false
 *                                         (if parameter doesn't depend on each event then dynamic parameter is false.
 *                                         In Source, only use static parameter)",
 *                               optional= "true/false, defaultValue= if it is optional then assign a default value
 *                                         according to the type."),
 * },
 * //If Source system configurations will need then
 * systemParameters = {
 * {@literal @}SystemParameter(name = "The name of the first  system parameter",
 *                                      description="The description of the first system parameter." ,
 *                                      defaultValue = "the default value of the system parameter.",
 *                                      possibleParameter="the possible value of the system parameter.",
 *                               ),
 * },
 * examples = {
 * {@literal @}Example(syntax = "sample query with Source annotation that explain how extension use in Siddhi."
 *                              description =" The description of the given example's query."
 *                      ),
 * }
 * )
 * </code></pre>
 */

@Extension(
        name = "prometheus",
        namespace = "source",
        description = "The source consumes Prometheus metrics as Siddhi events which are being exported from the \n" +
                "specified url through http requests. According to the source configuration, it analyses metrics " +
                "from the text response \n" +
                "and send them as Siddhi events through key-value mapping. Prometheus source supports HTTP and HTTPS " +
                "\n" +
                "schemes for scraping metrics through http requests. The user can retrieve metrics of types \n" +
                "counter, gauge, histogram and summary. The required Prometheus metric can be specified \n" +
                "inside the source configuration using the metric name, job name, instance and grouping keys.\n" +
                "Since the source retrieves the metrics from a text response from the target, it is advised to use " +
                "\'string\' attribute type \n for the attributes that correspond the Prometheus metric labels.",
        parameters = {
                @Parameter(name = "target.url",
                        description = "This property specifies the target url where the Prometheus metrics are " +
                                "exported in text format.",
                        defaultValue = "http://localhost:9090/metrics",
                        optional = true,
                        type = DataType.STRING),
                @Parameter(
                        name = "scrape.interval",
                        description = "This property specifies the time interval that the source should make an HTTP " +
                                "request to the  provided target url (in seconds). By default, the source will " +
                                "scrape metrics within 60 seconds interval.",
                        defaultValue = "60",
                        optional = true,
                        type = {DataType.INT}
                ),
                @Parameter(
                        name = "scrape.timeout",
                        description = "This property is the time duration in seconds for a scrape request to get " +
                                "timed-out if the server at the url does not respond. By default, the property" +
                                " takes 10 seconds to time-out. ",
                        defaultValue = "10",
                        optional = true,
                        type = {DataType.INT}
                ),
                @Parameter(
                        name = "scheme",
                        description = "This property specifies the scheme of the target URL.\n The supported schemes" +
                                " are HTTP and HTTPS.",
                        defaultValue = "HTTP",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "metric.name",
                        description = "This property specifies the name of the metrics that is to be fetched. By " +
                                "default, metric name will be set according to the name of the stream. The metric " +
                                "name must match the regex format [a-zA-Z_:][a-zA-Z0-9_:]* ",
                        defaultValue = "Stream name",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "metric.type",
                        description = "This property specifies the type of the Prometheus metric that is required. " +
                                "needed to be fetched. \n The supported metric types are \'counter\', \'gauge\'," +
                                " \'histogram\' and \'summary\'. ",
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "value.attribute",
                        description = "The name of the attribute in stream definition which specifies the metric " +
                                "value. The defined value attribute must be included inside the stream attributes. \n" +
                                "By default, the value attribute is specified as \'value\'.\n The value attribute " +
                                "does not support 'STRING', 'OBJECT' and 'BOOLEAN' attribute types in the stream " +
                                "definition." ,
                        optional = true,
                        defaultValue = "value",
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "username",
                        description = "This property specifies the username that has to be added in the authorization" +
                                " header of the HTTP request, if basic authentication is enabled at the target. It " +
                                "is required to specify both username and password to enable basic authentication. " +
                                "If one of the parameter is not given by user then an error is logged in the console.",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "password",
                        description = "This property specifies the password that has to be added in the authorization" +
                                " header of the request, if basic authentication is enabled at the target. It " +
                                "is required to specify both username and password to enable basic authentication. " +
                                "If one of the parameter is not given by user then an error is logged in the console.",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "client.truststore.file",
                        description = "The file path to the location of truststore that the client needs to send for " +
                                "https requests through 'https' protocol.",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "client.truststore.password",
                        description = " The password for client-truststore to send https requests. A custom password " +
                                "can be specified if required. ",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "headers",
                        description = "headers that should be included as HTTP request headers in the request. " +
                                "The format of the supported input is as follows, \n" +
                                "\'header1:value1\',\'header2:value2\'",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "job",
                        description = " This property defines the job name of the exported Prometheus metrics " +
                                "that has to be fetched.",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "instance",
                        description = "This property defines the instance of the exported Prometheus metrics " +
                                "that has to be fetched.",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "grouping.key",
                        description = "This parameter specifies the grouping key of the required metrics in " +
                                "key-value pairs. Grouping key is used if the metrics are exported by Prometheus" +
                                " pushGateway in order to distinguish the metrics from already existing metrics. " +
                                "The expected format of the grouping key is as follows: \n" +
                                "\'key1:value1\',\'key2:value2\'",
                        defaultValue = " ",
                        optional = true,
                        type = {DataType.STRING}
                ),
        },
        examples = {
                @Example(
                        syntax = "@source(type= 'prometheus', target.url= 'http://localhost:9080/metrics', \n" +
                                "metric.type= 'counter', metric.name= 'sweet_production_counter', value.attribute= " +
                                "'amount', @map(type= ‘keyvalue’))\n" +
                                "define stream FooStream1(metric_name string, metric_type string, help string,\n " +
                                "subtype string, name string, quantity string, amount double);\n",
                        description = "In this example, the prometheus source will make an http request to the " +
                                "\'target.url\' and analyse the response. From the analysed response, the source " +
                                "retrieves the Prometheus counter metrics with name 'sweet_production_counter' and " +
                                "converts the filtered metrics into Siddhi events using the key-value mapper." +
                                "\nThe generated maps will have keys and values as follows, \n" +
                                "  metric_name  -> sweet_production_counter\n" +
                                "  metric_type  -> counter\n" +
                                "  help  -> <help_string_of_metric>\n" +
                                "  subtype  -> null\n" +
                                "  name -> <value_of_label_name>\n" +
                                "  quantity -> <value_of_label_quantity>\n" +
                                "  amount -> <value_of_metric>\n"
                ),
                @Example(
                        syntax = "@source(type= 'prometheus', target.url= 'http://localhost:9080/metrics', \n" +
                                "metric.type= 'summary', metric.name= 'sweet_production_summary', @map(type= " +
                                "‘keyvalue’))\n define stream FooStream2(metric_name string, metric_type string, help" +
                                " string,\n subtype string, name string, quantity string, quantile string, value " +
                                "double);\n",
                        description = "In this example, the prometheus source will make an http request to the " +
                                "\'target.url\' and analyse the response. From the analysed response, the source " +
                                "retrieves the Prometheus summary metrics with name 'sweet_production_summary' and " +
                                "converts the filtered metrics into Siddhi events using the key-value mapper." +
                                "\nThe generated maps will have keys and values as follows, \n" +
                                "  metric_name  -> sweet_production_summary\n" +
                                "  metric_type  -> summary\n" +
                                "  help  -> <help_string_of_metric>\n" +
                                "  subtype  -> <'sum'/'count'/'null'>\n" +
                                "  name -> <value_of_label_name>\n" +
                                "  quantity -> <value_of_label_quantity>\n" +
                                "  quantile  -> <value of the quantile>\n" +
                                "  value -> <value_of_metric>\n"
                ),
                @Example(
                        syntax = "@source(type= 'prometheus', target.url= 'http://localhost:9080/metrics', \n" +
                                "metric.type= 'histogram', metric.name= 'sweet_production_histogram', @map(type= " +
                                "‘keyvalue’))\n" +
                                "define stream FooStream3(metric_name string, metric_type string, help string,\n " +
                                "subtype string, name string, quantity string, le string, value double);\n",
                        description = "In this example, the prometheus source will make an http request to the " +
                                "\'target.url\' and analyse the response. From the analysed response, the source " +
                                "retrieves the Prometheus histogram metrics with name 'sweet_production_histogram' " +
                                "and converts the filtered metrics into Siddhi events using the key-value mapper." +
                                "\nThe generated maps will have keys and values as follows, \n" +
                                "  metric_name  -> sweet_production_histogram\n" +
                                "  metric_type  -> histogram\n" +
                                "  help  -> <help_string_of_metric>\n" +
                                "  subtype  -> <'sum'/'count'/'bucket'>\n" +
                                "  name -> <value_of_label_name>\n" +
                                "  quantity -> <value_of_label_quantity>\n" +
                                "  le  -> <value of the bucket>\n" +
                                "  value -> <value_of_metric>\n"
                )
        },
        systemParameter = {
                @SystemParameter(
                        name = "targetURL",
                        description = "This property configure the URL of the target where the Prometheus metrics " +
                                "are exported in text format.",
                        defaultValue = "'http://localhost:9090/metrics'",
                        possibleParameters = "Any valid URL which exports Prometheus metrics in text format"
                ),
                @SystemParameter(
                        name = "scrapeInterval",
                        description = "The default time interval in seconds for the Prometheus source to make HTTP " +
                                "requests to the target URL.",
                        defaultValue = "60",
                        possibleParameters = "Any integer value"
                ),
                @SystemParameter(
                        name = "scrapeTimeout",
                        description = "This default time duration for an HTTP request to time-out if the server " +
                                "at the URL does not respond. (in seconds) ",
                        defaultValue = "10",
                        possibleParameters = "Any integer value"
                ),
                @SystemParameter(
                        name = "scheme",
                        description = "The scheme of the target for Prometheus source to make HTTP requests." +
                                " The supported schemes are HTTP and HTTPS.",
                        defaultValue = "HTTP",
                        possibleParameters = "HTTP or HTTPS"
                ),
                @SystemParameter(
                        name = "username",
                        description = "The username that has to be added in the authorization header of the HTTP " +
                                "request, if basic authentication is enabled at the target. It is required to " +
                                "specify both username and password to enable basic authentication. If one of " +
                                "the parameter is not given by user then an error is logged in the console.",
                        defaultValue = " ",
                        possibleParameters = "Any string"
                ),
                @SystemParameter(
                        name = "password",
                        description = "The password that has to be added in the authorization header of the HTTP " +
                                "request, if basic authentication is enabled at the target. It is required to " +
                                "specify both username and password to enable basic authentication. If one of" +
                                " the parameter is not given by user then an error is logged in the console.",
                        defaultValue = " ",
                        possibleParameters = "Any string"
                ),
                @SystemParameter(
                        name = "trustStoreFile",
                        description = "The default file path to the location of truststore that the client needs " +
                                "to send for HTTPS requests through 'HTTPS' protocol.",
                        defaultValue = "${carbon.home}/resources/security/client-truststore.jks",
                        possibleParameters = "Any valid path for the truststore file"
                ),
                @SystemParameter(
                        name = "trustStorePassword",
                        description = "The default password for the client-truststore to send HTTPS requests.",
                        defaultValue = "wso2carbon",
                        possibleParameters = "Any string"
                ),
                @SystemParameter(
                        name = "headers",
                        description = "The headers that should be included as HTTP request headers in the scrape " +
                                "request. The format of the supported input is as follows, \n" +
                                "\"\'header1:value1\',\'header2:value2\'\"",
                        defaultValue = " ",
                        possibleParameters = "Any valid http headers"
                ),
                @SystemParameter(
                        name = "job",
                        description = " The default job name of the exported Prometheus metrics " +
                                "that has to be fetched.",
                        defaultValue = " ",
                        possibleParameters = "Any valid job name"
                ),
                @SystemParameter(
                        name = "instance",
                        description = "The default instance of the exported Prometheus metrics " +
                                "that has to be fetched.",
                        defaultValue = " ",
                        possibleParameters = "Any valid instance name"
                ),
                @SystemParameter(
                        name = "groupingKey",
                        description = "The default grouping key of the required Prometheus metrics in key-value " +
                                "pairs. Grouping key is used if the metrics are exported by Prometheus pushGateway " +
                                "in order to distinguish the metrics from already existing metrics. " +
                                "The expected format of the grouping key is as follows: \n" +
                                "\"\'key1:value1\',\'key2:value2\'\"",
                        defaultValue = " ",
                        possibleParameters = "Any valid grouping key pairs"
                )
        }
)

// for more information refer https://wso2.github.io/siddhi/documentation/siddhi-4.0/#sources
public class PrometheusSource extends Source {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private String targetURL;
    private String streamName;
    private String scheme;
    private double scrapeInterval;

    private PrometheusScraper prometheusScraper;


    /**
     * The initialization method for {@link Source}, will be called before other methods. It used to validate
     * all configurations and to get initial values.
     *
     * @param sourceEventListener After receiving events, the source should trigger onEvent() of this listener.
     *                            Listener will then pass on the events to the appropriate mappers for processing .
     * @param optionHolder        Option holder containing static configuration related to the {@link Source}
     * @param configReader        ConfigReader is used to read the {@link Source} related system configuration.
     * @param siddhiAppContext    the context of the SiddhiApp used to get Siddhi
     *                            related utility functions.
     */
    @Override
    public void init(SourceEventListener sourceEventListener, OptionHolder optionHolder,
                     String[] requestedTransportPropertyNames, ConfigReader configReader,
                     SiddhiAppContext siddhiAppContext) {
        streamName = sourceEventListener.getStreamDefinition().getId();
        PrometheusSourceUtil.setStreamName(streamName);

        initPrometheusScraper(optionHolder, configReader, sourceEventListener, siddhiAppContext);
        configureMetricAnalyser(optionHolder, configReader, siddhiAppContext);
    }

    private void initPrometheusScraper(OptionHolder optionHolder, ConfigReader configReader,
                                       SourceEventListener sourceEventListener, SiddhiAppContext siddhiAppContext) {

        this.targetURL = optionHolder.validateAndGetStaticValue(PrometheusConstants.TARGET_URL,
                configReader.readConfig(PrometheusConstants.TARGET_URL_CONFIGURATION,
                        PrometheusConstants.EMPTY_STRING));
        this.scheme = optionHolder.validateAndGetStaticValue(PrometheusConstants.SCHEME, configReader
                .readConfig(PrometheusConstants.SCHEME_CONFIGURATION, PrometheusConstants.HTTP_SCHEME));
        if (!(scheme.equalsIgnoreCase(PrometheusConstants.HTTP_SCHEME) || scheme.equalsIgnoreCase(
                PrometheusConstants.HTTPS_SCHEME))) {
            throw new SiddhiAppCreationException("The field \'scheme\' contains unsupported value in " +
                    streamName + " of " + PrometheusConstants.PROMETHEUS_SOURCE);
        }
        if (EMPTY_STRING.equals(targetURL)) {
            throw new SiddhiAppCreationException("The target URL field found empty but it is a Mandatory field of " +
                    "" + PrometheusConstants.PROMETHEUS_SOURCE + " in " + streamName);
        }
        try {
            URL url = new URL(targetURL);
            if (!(url.getProtocol()).equalsIgnoreCase(scheme)) {
                throw new SiddhiAppCreationException("The provided scheme and the scheme of target URL are " +
                        "not matching in Prometheus source associated with stream " + streamName);
            }
        } catch (MalformedURLException e) {
            throw new SiddhiAppCreationException("The Prometheus source associated with stream " + streamName +
                    " contains an invalid value for target URL");
        }
        scrapeInterval = Double.parseDouble(optionHolder.validateAndGetStaticValue(
                PrometheusConstants.SCRAPE_INTERVAL, configReader.readConfig(
                        PrometheusConstants.SCRAPE_INTERVAL_CONFIGURATION,
                        PrometheusConstants.DEFAULT_SCRAPE_INTERVAL)));
        validateNegativeValue(scrapeInterval);
        double scrapeTimeout = Double.parseDouble(optionHolder.validateAndGetStaticValue(
                PrometheusConstants.SCRAPE_TIMEOUT,
                configReader.readConfig(PrometheusConstants.SCRAPE_TIMEOUT_CONFIGURATION,
                        PrometheusConstants.DEFAULT_SCRAPE_TIMEOUT)));
        validateNegativeValue(scrapeTimeout);
        String userName = optionHolder.validateAndGetStaticValue(PrometheusConstants.USERNAME_BASIC_AUTH,
                configReader.readConfig(PrometheusConstants.USERNAME_BASIC_AUTH_CONFIGURATION, EMPTY_STRING));
        String password = optionHolder.validateAndGetStaticValue(PrometheusConstants.PASSWORD_BASIC_AUTH,
                configReader.readConfig(PrometheusConstants.PASSWORD_BASIC_AUTH_CONFIGURATION, EMPTY_STRING));
        String clientStoreFile = optionHolder.validateAndGetStaticValue(PrometheusConstants.TRUSTSTORE_FILE,
                PrometheusSourceUtil.trustStorePath(configReader));
        String clientStorePassword = optionHolder.validateAndGetStaticValue(PrometheusConstants.TRUSTSTORE_PASSWORD,
                PrometheusSourceUtil.trustStorePassword(configReader));
        String headers = optionHolder.validateAndGetStaticValue(PrometheusConstants.REQUEST_HEADERS,
                configReader.readConfig(PrometheusConstants.REQUEST_HEADERS_CONFIGURATION, EMPTY_STRING));

        List<Header> headerList = PrometheusSourceUtil.getHeaders(headers, streamName);
        this.prometheusScraper = new PrometheusScraper(targetURL, scheme, scrapeTimeout, headerList,
                sourceEventListener);
        if ((EMPTY_STRING.equals(userName) ^ EMPTY_STRING.equals(password))) {
            throw new SiddhiAppCreationException("Please provide user name and password in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " associated with the stream " + streamName + " in " +
                    "Siddhi app " + siddhiAppContext.getName());
        } else if (!(EMPTY_STRING.equals(userName) || EMPTY_STRING.equals(password))) {
            prometheusScraper.setAuthorizationHeader(userName, password);
        }

        if (PrometheusConstants.HTTPS_SCHEME.equalsIgnoreCase(scheme) && ((clientStoreFile.equals(EMPTY_STRING)) ||
                (clientStorePassword.equals(EMPTY_STRING)))) {
            throw new ExceptionInInitializerError("Client trustStore file path or password are empty while " +
                    "default scheme is 'https'. Please provide client " +
                    "trustStore file path and password in " + streamName + " of " +
                    PrometheusConstants.PROMETHEUS_SOURCE);
        }
        if (PrometheusConstants.HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            prometheusScraper.setHttpsProperties(clientStoreFile, clientStorePassword);
        }
    }

    private void configureMetricAnalyser(OptionHolder optionHolder, ConfigReader configReader,
                                         SiddhiAppContext siddhiAppContext) {
        String metricName = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_NAME, streamName);
        MetricType metricType = MetricType.assignMetricType(optionHolder.
                        validateAndGetStaticValue(PrometheusConstants.METRIC_TYPE),
                streamName);
        String job = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_JOB,
                configReader.readConfig(PrometheusConstants.METRIC_JOB_CONFIGURATION, EMPTY_STRING));
        String instance = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_INSTANCE,
                configReader.readConfig(PrometheusConstants.METRIC_INSTANCE_CONFIGURATION, EMPTY_STRING));
        Map<String, String> groupingKeyMap = PrometheusSourceUtil.populateStringMap(
                optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_GROUPING_KEY,
                        PrometheusConstants.EMPTY_STRING));
        String valueAttribute = optionHolder.validateAndGetStaticValue(
                PrometheusConstants.VALUE_ATTRIBUTE, PrometheusConstants.VALUE_STRING).trim();
        Attribute.Type valueType;
        try {
            valueType = getStreamDefinition().getAttributeType(valueAttribute);
            if (valueType.equals(Attribute.Type.STRING) || valueType.equals(Attribute.Type.BOOL) ||
                    valueType.equals(Attribute.Type.OBJECT)) {
                throw new SiddhiAppCreationException("The field value attribute \'" + valueAttribute + "\' contains " +
                        "unsupported type in " + PrometheusConstants.PROMETHEUS_SOURCE + " associated with stream \'"
                        + streamName + "\'");
            }
        } catch (AttributeNotExistException exception) {
            throw new SiddhiAppCreationException("The value attribute \'" + valueAttribute + "\' is not found " +
                    "in " + PrometheusConstants.PROMETHEUS_SOURCE + " associated with stream \'" + streamName + "\'");
        }

        prometheusScraper.setMetricProperties(metricName, metricType, job, instance, groupingKeyMap, valueAttribute,
                valueType);
    }

    private void validateNegativeValue(double value) {
        if (value < 0) {
            throw new SiddhiAppCreationException("The value of fields scrape interval or scrape timeout from " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " cannot be negative in " + streamName);
        }
    }

    /**
     * Returns the list of classes which this source can output.
     *
     * @return Array of classes that will be output by the source.
     * Null or empty array if it can produce any type of class.
     */
    @Override
    public Class[] getOutputEventClasses() {
        return new Class[]{Map.class};
    }

    /**
     * Initially Called to connect to the end point for start retrieving the messages asynchronously .
     *
     * @param connectionCallback Callback to pass the ConnectionUnavailableException in case of connection failure after
     *                           initial successful connection. (can be used when events are receiving asynchronously)
     * @throws ConnectionUnavailableException if it cannot connect to the source backend immediately.
     */
    @Override
    public void connect(ConnectionCallback connectionCallback) throws ConnectionUnavailableException {
        prometheusScraper.connectHTTPClient();
        executorService.scheduleWithFixedDelay(prometheusScraper, 0, (long) scrapeInterval, TimeUnit.SECONDS);
    }

    /**
     * This method can be called when it is needed to disconnect from the end point.
     */
    @Override
    public void disconnect() {
        executorService.shutdown();
        prometheusScraper.pause();
    }

    /**
     * .
     * Called at the end to clean all the resources consumed by the {@link Source}
     */
    @Override
    public void destroy() {
        prometheusScraper.clearPrometheusScraper();
    }

    /**
     * .
     * Called to pause event consumption
     */
    @Override
    public void pause() {
        prometheusScraper.pause();
    }

    /**
     * .
     * Called to resume event consumption
     */
    @Override
    public void resume() {
        prometheusScraper.resume();
    }

    /**
     * .
     * Used to collect the serializable state of the processing element, that need to be
     * persisted for the reconstructing the element to the same state on a different point of time
     *
     * @return stateful objects of the processing element as a map
     */
    @Override
    public Map<String, Object> currentState() {
        Map<String, Object> currentState = new HashMap<>();
        currentState.put(PrometheusConstants.LAST_RETRIEVED_SAMPLES, prometheusScraper.getLastValidResponse());
        return currentState;
    }

    /**
     * Used to restore serialized state of the processing element, for reconstructing
     * the element to the same state as if was on a previous point of time.
     *
     * @param map the stateful objects of the processing element as a map.
     *            This map will have the  same keys that is created upon calling currentState() method.
     */
    @Override
    public void restoreState(Map<String, Object> map) {
        prometheusScraper.setLastValidResponse((List<String>) map.get(PrometheusConstants.LAST_RETRIEVED_SAMPLES));

    }

}

