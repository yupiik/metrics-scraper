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
                        "\"" + field.getKey() + "\":" + value + "," +
                        "\"labels\": {" + labels.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," +
                        "\"tags\": {" + tags.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}," +
                        "\"type\":\"" + type.name() + "\"," +
                        "\"@timestamp\":\"" + timestamp + "\"" +
                        "}";
        }
}
