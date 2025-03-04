/*
 * Copyright (c) 2020-2022 - Yupiik SAS - https://www.yupiik.com - All right reserved
 *
 * This software and related documentation are provided under a license agreement containing restrictions on use and
 * disclosure and are protected by intellectual property laws. Except as expressly permitted in your license agreement
 * or allowed by law, you may not use, copy, reproduce, translate, broadcast, modify, license, transmit, distribute,
 * exhibit, perform, publish, or display any part, in any form, or by any means. Reverse engineering, disassembly, or
 * decompilation of this software, unless required by law for interoperability, is prohibited.
 *
 * The information contained herein is subject to change without notice and is not warranted to be error-free. If you
 * find any errors, please report them to us in writing.
 *
 * This software is developed for general use in a variety of information management applications. It is not developed
 * or intended for use in any inherently dangerous applications, including applications that may create a risk of personal
 * injury. If you use this software or hardware in dangerous applications, then you shall be responsible to take all
 * appropriate fail-safe, backup, redundancy, and other measures to ensure its safe use. Yupiik SAS and its affiliates
 * disclaim any liability for any damages caused by use of this software or hardware in dangerous applications.
 *
 * Yupiik and Meecrogate are registered trademarks of Yupiik SAS and/or its affiliates. Other names may be trademarks
 * of their respective owners.
 *
 * This software and documentation may provide access to or information about content, products, and services from third
 * parties. Yupiik SAS and its affiliates are not responsible for and expressly disclaim all warranties of any kind with
 * respect to third-party content, products, and services unless otherwise set forth in an applicable agreement between
 * you and Yupiik SAS. Yupiik SAS and its affiliates will not be responsible for any loss, costs, or damages incurred
 * due to your access to or use of third-party content, products, or services, except as set forth in an applicable
 * agreement between you and Yupiik SAS.
 */
package io.yupiik.metrics.metricsscrapper.test.protocol;

import io.yupiik.fusion.testing.Fusion;
import io.yupiik.metrics.metricsscrapper.model.metrics.MetricInstance;
import io.yupiik.metrics.metricsscrapper.model.metrics.Metrics;
import io.yupiik.metrics.metricsscrapper.protocol.OpenMetricsReader;
import io.yupiik.metrics.metricsscrapper.test.ApplicationSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ApplicationSupport
class OpenMetricsReaderTest {

    @Test
    void parse(@Fusion final OpenMetricsReader reader) {
        final Metrics metrics = reader.read("" +
                "# HELP http_requests_total The total number of HTTP requests.\n" +
                "# TYPE http_requests_total counter\n" +
                "http_requests_total{method=\"post\",code=\"200\"} 1027 1395066363000\n" +
                "http_requests_total{method=\"post\",code=\"400\"}    3 1395066363000\n" +
                "\n" +
                "# Escaping in label values:\n" +
                "msdos_file_access_time_seconds{path=\"C:\\DIR\\FILE.TXT\",error=\"Cannot find file:\\n\"FILE.TXT\"\"} 1.458255915e9\n" +
                "\n" +
                "# Minimalistic line:\n" +
                "metric_without_timestamp_and_labels 12.47\n" +
                "\n" +
                "# A weird metric from before the epoch:\n" +
                "something_weird{problem=\"division by zero\"} +Inf -3982045\n" +
                "\n" +
                "# A histogram, which has a pretty complex representation in the text format:\n" +
                "# HELP http_request_duration_seconds A histogram of the request duration.\n" +
                "# TYPE http_request_duration_seconds histogram\n" +
                "http_request_duration_seconds_bucket{le=\"0.05\"} 24054\n" +
                "http_request_duration_seconds_bucket{le=\"0.1\"} 33444\n" +
                "http_request_duration_seconds_bucket{le=\"0.2\"} 100392\n" +
                "http_request_duration_seconds_bucket{le=\"0.5\"} 129389\n" +
                "http_request_duration_seconds_bucket{le=\"1\"} 133988\n" +
                "http_request_duration_seconds_bucket{le=\"+Inf\"} 144320\n" +
                "http_request_duration_seconds_sum 53423\n" +
                "http_request_duration_seconds_count 144320\n" +
                "\n" +
                "# Finally a summary, which has a complex representation, too:\n" +
                "# HELP rpc_duration_seconds A summary of the RPC duration in seconds.\n" +
                "# TYPE rpc_duration_seconds summary\n" +
                "rpc_duration_seconds{quantile=\"0.01\"} 3102\n" +
                "rpc_duration_seconds{quantile=\"0.05\"} 3272\n" +
                "rpc_duration_seconds{quantile=\"0.5\"} 4773\n" +
                "rpc_duration_seconds{quantile=\"0.9\"} 9001\n" +
                "rpc_duration_seconds{quantile=\"0.99\"} 76656\n" +
                "rpc_duration_seconds_sum 1.7560473e+07\n" +
                "rpc_duration_seconds_count 2693", 1234);
        assertEquals("" +
                        "COUNTERS:\n" +
                        "http_requests_total: 1395066363000: 1027.0 /{code=200, method=post}\n" +
                        "http_requests_total: 1395066363000: 3.0 /{code=400, method=post}\n" +
                        "GAUGES:\n" +
                        "\n" +
                        "UNTYPED:\n" +
                        "metric_without_timestamp_and_labels: 1234: 12.47 /{}\n" +
                        "msdos_file_access_time_seconds: 1234: 1.458255915E9 /{error=Cannot find file:\\n\"FILE.TXT\", path=C:\\DIR\\FILE.TXT}\n" +
                        "something_weird: -3982045: Infinity /{problem=division by zero}\n" +
                        "HISTOGRAMS:\n" +
                        "http_request_duration_seconds: 1234: 53423.0, 144320.0 /{}/0.05=24054.0,0.1=33444.0,0.2=100392.0,0.5=129389.0,1=133988.0\n" +
                        "SUMMARIES:\n" +
                        "rpc_duration_seconds: 1234: 1.7560473E7, 2693.0 /{}/0.01=3102.0,0.05=3272.0,0.5=4773.0,0.9=9001.0,0.99=76656.0",
                format(metrics));
    }

    private String format(final Metrics metrics) {
        return "COUNTERS:\n" + metrics.getCounters().stream()
                .sorted(comparing(MetricInstance::getName))
                .map(it -> it.getName() + ": " + it.getTimestamp() + ": " + it.getValue() + " /" + it.getTags())
                .collect(joining("\n")) + '\n' +
                "GAUGES:\n" + metrics.getGauges().stream()
                .sorted(comparing(MetricInstance::getName))
                .map(it -> it.getName() + ": " + it.getTimestamp() + ": " + it.getValue() + " /" + it.getTags())
                .collect(joining("\n")) + '\n' +
                "UNTYPED:\n" + metrics.getUntyped().stream()
                .sorted(comparing(MetricInstance::getName))
                .map(it -> it.getName() + ": " + it.getTimestamp() + ": " + it.getValue() + " /" + it.getTags())
                .collect(joining("\n")) + '\n' +
                "HISTOGRAMS:\n" + metrics.getHistogram().stream()
                .sorted(comparing(MetricInstance::getName))
                .map(it -> it.getName() + ": " + it.getTimestamp() + ": " + it.getSum() + ", " + it.getCount() + " /" + it.getTags() + '/' + it.getBuckets().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(b -> b.getKey() + "=" + b.getValue())
                        .collect(joining(",")))
                .collect(joining("\n")) + '\n' +
                "SUMMARIES:\n" + metrics.getSummary().stream()
                .sorted(comparing(MetricInstance::getName))
                .map(it -> it.getName() + ": " + it.getTimestamp() + ": " + it.getSum() + ", " + it.getCount() + " /" + it.getTags() + '/' + it.getQuantiles().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(b -> b.getKey() + "=" + b.getValue())
                        .collect(joining(",")))
                .collect(joining("\n"));
    }
}
