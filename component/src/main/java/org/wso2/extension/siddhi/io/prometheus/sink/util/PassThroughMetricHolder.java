package org.wso2.extension.siddhi.io.prometheus.sink.util;
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

import io.prometheus.client.Collector;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusConstants;
import org.wso2.extension.siddhi.io.prometheus.util.PrometheusSinkUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A singleton class to initialize metric in HTTP response.
 */
class PassThroughMetricHolder {


    private static List<String> recordedMetricsList = new ArrayList<>();

    private PassThroughMetricHolder() {
    }

    private static void recordMetric(String metric_name, Collector.Type metric_type) {
        recordedMetricsList.add(metric_name);
    }

    static void writeMetricProperties(String metric_name, Collector.Type metric_type, String metric_help,
                                      StringBuilder builder) {
        if (!recordedMetricsList.contains(metric_name)) {
            recordedMetricsList.add(metric_name);
            builder.append("# HELP ").append(metric_name);
            builder.append(PrometheusConstants.SPACE_STRING).append(metric_help);
            builder.append(System.lineSeparator());
            builder.append("# TYPE ").append(metric_name).append(PrometheusConstants.SPACE_STRING);
            builder.append(PrometheusSinkUtil.getMetricTypeString(metric_type));
            builder.append(System.lineSeparator());
        }
    }


}
