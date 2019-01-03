package org.wso2.extension.siddhi.io.prometheus.sink.util;

import io.prometheus.client.CollectorRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * A singleton class to initialize registry.
 */
class PrometheusRegistryHolder {

    private static Map<Integer, CollectorRegistry> registryMap = new HashMap<>();

    private PrometheusRegistryHolder() {
    }

    private static CollectorRegistry registerRegistry(int hashKey) {
        CollectorRegistry registry = new CollectorRegistry();
        registryMap.put(hashKey, registry);
        return registry;

    }

    static CollectorRegistry retrieveRegistry(String host, int port) {
        int hashKey = (host + port).hashCode();
        if (registryMap.containsKey(hashKey)) {
            return registryMap.get(hashKey);
        } else {
            return registerRegistry(hashKey);
        }
    }


}
