/*
 * Copyright (c) 2025 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.metrics.scraper.test.protocol;

import io.yupiik.metrics.scraper.model.metrics.Metrics;
import io.yupiik.metrics.scraper.protocol.OpenMetricsReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;


class OpenMetricsReaderTest {
    private final OpenMetricsReader reader = new OpenMetricsReader();

    @Test
    void parse() {
        System.out.println("Parse simple metrics");
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
        assertNotNull(metrics);
    }

    @Test
    void parseComplex() throws IOException {
        System.out.println("Parse complex metrics");
        final String content = new String(Files.readAllBytes(Paths.get("src/test/resources/metrics.txt")));
        final Metrics metrics = reader.read(content, 1234);
        assertNotNull(metrics);
    }

}
