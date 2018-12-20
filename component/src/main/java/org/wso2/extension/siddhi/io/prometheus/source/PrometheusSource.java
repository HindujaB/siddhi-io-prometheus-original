package org.wso2.extension.siddhi.io.prometheus.source;

import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSourceUtil;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.stream.input.source.Source;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.OptionHolder;
import org.wso2.siddhi.query.api.SiddhiApp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        description = " ",
        parameters = {
                /*@Parameter(name = " ",
                        description = " " ,
                        dynamic = false/true,
                        optional = true/false, defaultValue = " ",
                        type = {DataType.INT, DataType.BOOL, DataType.STRING, DataType.DOUBLE, }),
                        type = {DataType.INT, DataType.BOOL, DataType.STRING, DataType.DOUBLE, }),*/
        },
        examples = {
                @Example(
                        syntax = " ",
                        description = " "
                )
        }
)

// for more information refer https://wso2.github.io/siddhi/documentation/siddhi-4.0/#sources
public class PrometheusSource extends Source {

    private static final Logger log = Logger.getLogger(PrometheusSource.class);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private PrometheusMetricAnalyser metricAnalyser;
    private String targetURL;
    private String siddhiAppName;
    private String streamName;
    private String scheme;

    private PrometheusScraper prometheusScraper;



    /**
     * The initialization method for {@link Source}, will be called before other methods. It used to validate
     * all configurations and to get initial values.
     *
     * @param sourceEventListener After receiving events, the source should trigger onEvent() of this listener.
     *                            Listener will then pass on the events to the appropriate mappers for processing .
     * @param optionHolder        Option holder containing static configuration related to the {@link Source}
     * @param configReader        ConfigReader is used to read the {@link Source} related system configuration.
     * @param siddhiAppContext    the context of the {@link SiddhiApp} used to get Siddhi
     *                            related utility functions.
     */
    @Override
    public void init(SourceEventListener sourceEventListener, OptionHolder optionHolder,
                     String[] requestedTransportPropertyNames, ConfigReader configReader,
                     SiddhiAppContext siddhiAppContext) {
        siddhiAppName = siddhiAppContext.getName();
        streamName = sourceEventListener.getStreamDefinition().getId();
        initPrometheusScraper(optionHolder, configReader, sourceEventListener, siddhiAppContext);
        configureMetricAnalyser(optionHolder, configReader, siddhiAppContext);
    }

    private void configureMetricAnalyser(OptionHolder optionHolder, ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
        String metricName = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_NAME, streamName);
        MetricType metricType = MetricType.assignMetricType(optionHolder.
                validateAndGetStaticValue(PrometheusConstants.METRIC_TYPE));
        String job = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_JOB,
                configReader.readConfig(PrometheusConstants.METRIC_JOB_CONFIGURATION, EMPTY_STRING));
        String instance = optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_INSTANCE,
                configReader.readConfig(PrometheusConstants.METRIC_INSTANCE_CONFIGURATION, EMPTY_STRING));
        Map<String, String> groupingKeyMap = PrometheusSourceUtil.populateStringMap(
                optionHolder.validateAndGetStaticValue(PrometheusConstants.METRIC_GROUPING_KEY,
                        PrometheusConstants.EMPTY_STRING));
        prometheusScraper.setMetricProperties(metricName, metricType, job, instance, groupingKeyMap);
    }

    private void initPrometheusScraper(OptionHolder optionHolder, ConfigReader configReader,
                                       SourceEventListener sourceEventListener, SiddhiAppContext siddhiAppContext) {

        this.targetURL = optionHolder.validateAndGetStaticValue(PrometheusConstants.TARGET_URL,
                configReader.readConfig(PrometheusConstants.TARGET_URL_CONFIGURATION,
                        PrometheusConstants.EMPTY_STRING));
        this.scheme = optionHolder.validateAndGetStaticValue(PrometheusConstants.SCHEME, configReader
                .readConfig(PrometheusConstants.SCHEME_CONFIGURATION, PrometheusConstants.HTTP_SCHEME));
        if (!(scheme.equals(PrometheusConstants.HTTP_SCHEME) || scheme.equals(PrometheusConstants.HTTPS_SCHEME))) {
            throw new SiddhiAppCreationException("The field \'scheme\' contains unsupported value in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " in " + streamName);
        }
        if (EMPTY_STRING.equals(targetURL)) {
            throw new SiddhiAppCreationException("target URL field found empty but it is a Mandatory field of " +
                    "" + PrometheusConstants.PROMETHEUS_SOURCE + " in " + streamName);
        }
        try {
            URL url = new URL(targetURL);
            if (!(url.getProtocol()).equalsIgnoreCase(scheme)) {
                throw new SiddhiAppCreationException("The provided scheme and the scheme of target URL are " +
                        "not matching in Prometheus source with stream " + streamName);
            }
        } catch (MalformedURLException e) {
            throw new SiddhiAppCreationException("The Prometheus source contains an invalid value for target URL");
        }
        int scrapeInterval = Integer.parseInt(optionHolder.validateAndGetStaticValue(PrometheusConstants.SCRAPE_INTERVAL,
                configReader.readConfig(PrometheusConstants.SCRAPE_INTERVAL_CONFIGURATION,
                        PrometheusConstants.DEFAULT_SCRAPE_INTERVAL)));
        int scrapeTimeout = Integer.parseInt(optionHolder.validateAndGetStaticValue(PrometheusConstants.SCRAPE_TIMEOUT,
                configReader.readConfig(PrometheusConstants.SCRAPE_TIMEOUT_CONFIGURATION,
                        PrometheusConstants.DEFAULT_SCRAPE_TIMEOUT)));
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

        if (PrometheusConstants.HTTPS_SCHEME.equals(scheme) && ((clientStoreFile == null) || (clientStorePassword == null))) {
            throw new ExceptionInInitializerError("Client trustStore file path or password are empty while " +
                    "default scheme is 'https'. Please provide client " +
                    "trustStore file path and password in " + streamName);
        }

        Map<String, String> headersMap = PrometheusSourceUtil.populateStringMap(headers);
        this.prometheusScraper = new PrometheusScraper(targetURL, scheme, scrapeInterval, scrapeTimeout,
                headersMap, sourceEventListener);
        if ((EMPTY_STRING.equals(userName) ^
                EMPTY_STRING.equals(password))) {
            throw new SiddhiAppCreationException("Please provide user name and password in " +
                    PrometheusConstants.PROMETHEUS_SOURCE + " with the stream " + streamName + " in Siddhi app " +
                    siddhiAppContext.getName());
        } else if (!(EMPTY_STRING.equals(userName) || EMPTY_STRING.equals(password))) {
            prometheusScraper.setAuthorizationHeader(userName, password);
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
        executorService.execute(prometheusScraper);

    }

    /**
     * This method can be called when it is needed to disconnect from the end point.
     */
    @Override
    public void disconnect() {
        prometheusScraper.pause();
    }

    /**
     * .
     * Called at the end to clean all the resources consumed by the {@link Source}
     */
    @Override
    public void destroy() {
        prometheusScraper.pause();
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
        prometheusScraper.setLastValidResponse((List<String>)map.get(PrometheusConstants.LAST_RETRIEVED_SAMPLES));

    }
}

