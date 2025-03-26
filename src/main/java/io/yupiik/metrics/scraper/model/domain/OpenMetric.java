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
package io.yupiik.metrics.scraper.model.domain;

import io.yupiik.metrics.scraper.model.metrics.OpenMetricMetricType;

import java.util.Map;
import java.util.stream.Collectors;

public class OpenMetric {
        private String name;
        private KeyValue field;
        private Map<String, String> labels;
        private Map<String, String> tags;
        private OpenMetricMetricType type;
        private String timestamp;

        public OpenMetric(final String name, final KeyValue field, final Map<String, String> labels, final Map<String, String> tags,
                          final OpenMetricMetricType type, final String timestamp) {
                this.name = name;
                this.field = field;
                this.labels = labels;
                this.tags = tags;
                this.type = type;
                this.timestamp = timestamp;
        }

        public String json() {
                final var value = switch (field.getValue()) {
                    case String s -> "\"" + field.getValue() + "\"";
                    default -> field.getValue();
                };
                return "{" +
                        "\"name\":\"" + name + "\"," +
                        (field != null ? "\"" + field.getKey() + "\":" + value + "," : "") +
                        (labels != null ? "\"labels\": {" + labels.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," : "") +
                        (tags != null ? "\"tags\": {" + tags.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," : "") +
                        "\"type\":\"" + type.name() + "\"," +
                        "\"@timestamp\":\"" + timestamp + "\"" +
                        "}";
        }
}
