package io.yupiik.metrics.metricsscrapper.model.domain;

import io.yupiik.metrics.metricsscrapper.model.metrics.OpenMetricMetricType;

import java.util.Map;
import java.util.stream.Collectors;

public class OpenMetric {
        private String name;
        private KeyValue field;
        private Map<String, String> labels;
        private Map<String, String> tags;
        private OpenMetricMetricType type;
        private String timestamp;

        public OpenMetric(String name, KeyValue field, Map<String, String> labels, Map<String, String> tags, OpenMetricMetricType type, String timestamp) {
                this.name = name;
                this.field = field;
                this.labels = labels;
                this.tags = tags;
                this.type = type;
                this.timestamp = timestamp;
        }

        public String json() {
                return "{" +
                        "\"name\":\"" + name + "\"," +
                        "\"" + field.getKey() + "\":\"" + field.getValue() + "\"," +
                        "\"labels\": {" + labels.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," +
                        "\"tags\": {" + tags.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," +
                        "\"type\":\"" + type.name() + "\"," +
                        "\"@timestamp\":\"" + timestamp + "\"" +
                        "}";
        }
}
