package org.wso2.extension.siddhi.io.prometheus.util;

import org.wso2.carbon.messaging.Header;
import org.wso2.extension.siddhi.io.prometheus.source.PrometheusSourceException;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.SenderConfiguration;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class PrometheusSourceUtil {

    private static String streamName;

    public static void setStreamName(String streamName) {
        PrometheusSourceUtil.streamName = streamName;
    }

    public static Map<String, String> getURLProperties(String target, String scheme) throws MalformedURLException {
        URL targetURL = new URL(target);
        Map<String, String> httpURLProperties;
        httpURLProperties = new HashMap<>();
        httpURLProperties.put(Constants.TO, targetURL.getFile());
        String protocol = targetURL.getProtocol();
        httpURLProperties.put(Constants.PROTOCOL, protocol);
        httpURLProperties.put(Constants.HTTP_HOST, targetURL.getHost());
        int port;
        if (Constants.HTTPS_SCHEME.equalsIgnoreCase(protocol)) {
            port = targetURL.getPort() != -1 ? targetURL.getPort() : PrometheusConstants.DEFAULT_HTTPS_PORT;
        } else {
            port = targetURL.getPort() != -1 ? targetURL.getPort() : PrometheusConstants.DEFAULT_HTTP_PORT;
        }
        httpURLProperties.put(Constants.HTTP_PORT, Integer.toString(port));
        httpURLProperties.put(Constants.REQUEST_URL, targetURL.toString());
        return httpURLProperties;
    }

    public static String trustStorePath(ConfigReader configReader) {
        return configReader.readConfig(PrometheusConstants.TRUSTSTORE_FILE_CONFIGURATION,
                PrometheusConstants.TRUSTSTORE_PATH_VALUE);
    }

    public static String trustStorePassword(ConfigReader configReader) {
        return configReader.readConfig(PrometheusConstants.TRUSTSTORE_PASSWORD_CONFIGURATION,
                PrometheusConstants.TRUSTSTORE_PASSWORD_VALUE);
    }

    public static SenderConfiguration getSenderConfigurations(Map<String, String> urlProperties, String clientStoreFile,
                                                              String clientStorePass) {
        SenderConfiguration httpSender = new SenderConfiguration(urlProperties
                .get(Constants.HTTP_PORT));
        if (urlProperties.get(Constants.PROTOCOL).equals(PrometheusConstants.HTTPS_SCHEME)) {
            httpSender.setTrustStoreFile(clientStoreFile);
            httpSender.setTrustStorePass(clientStorePass);
            httpSender.setId(urlProperties.get(Constants.TO));
            httpSender.setScheme(urlProperties.get(Constants.PROTOCOL));
        } else {
            httpSender.setScheme(urlProperties.get(Constants.PROTOCOL));
        }
        return httpSender;
    }

    /**
     * Method is responsible of to convert string of headers to list of headers.
     * Example header format : 'name1:value1','name2:value2'
     *
     * @param headers string of headers list.
     * @return list of headers.
     */
    public static List<Header> getHeaders(String headers) {
        if (!headers.equals(PrometheusConstants.EMPTY_STRING)) {
            headers = headers.trim();
            headers = headers.substring(1, headers.length() - 1);
            List<Header> headersList = new ArrayList<>();
            if (!PrometheusConstants.EMPTY_STRING.equals(headers)) {
                String[] spam = headers.split(PrometheusConstants.KEY_VALUE_SEPARATOR);
                for (String headerValue : spam) {
                    String[] header = headerValue.split(PrometheusConstants.VALUE_SEPARATOR, 2);
                    if (header.length > 1) {
                        headersList.add(new Header(header[0], header[1]));
                    } else {
                        throw new PrometheusSourceException(
                                "Invalid header format. Please include as 'key1:value1','key2:value2',..");
                    }
                }
            }
            return headersList;
        } else {
            return null;
        }
    }

    /**
     * Method is responsible of to convert string of key value pairs to map of Strings.
     * Example input format : 'name1:value1','name2:value2'
     *
     * @param stringInput string of key value pairs.
     * @return map of stringInput.
     */
    public static Map<String, String> populateStringMap(String stringInput) {
        Map<String, String> stringMap = new HashMap<>();
        if (!PrometheusConstants.EMPTY_STRING.equals(stringInput)) {
            String[] headerList = stringInput.substring(1, stringInput.length() - 1)
                    .split(PrometheusConstants.KEY_VALUE_SEPARATOR);
            Arrays.stream(headerList).forEach(valueEntry -> {
                String[] entry = valueEntry.replaceAll(PrometheusConstants.SINGLE_QUOTE,
                        PrometheusConstants.EMPTY_STRING).split(PrometheusConstants.VALUE_SEPARATOR, 2);
                if (entry.length == 2) {
                    String key = entry[0];
                    String value = entry[1];
                    stringMap.put(key, value);
                } else {
                    throw new SiddhiAppCreationException(
                            "Invalid format for key-value input in " + streamName + " of " +
                                    PrometheusConstants.PROMETHEUS_SOURCE + ". Please include as " +
                                    "'key1:value1','key2:value2',..");
                }
            });
        }
        return stringMap;
    }

    public static List<Header> setHeaderList(Map<String, String> headers) {
        List<Header> headerList = new ArrayList<>();
        if (!headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerList.add(new Header(entry.getKey(), entry.getValue()));
            }
        }
        return headerList;
    }
}
