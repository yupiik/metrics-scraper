package io.yupiik.metrics.scraper.model.domain;

import io.yupiik.metrics.scraper.model.metrics.OpenMetricMetricType;

import java.util.Map;

public class Counter extends OpenMetric {
    public Counter(final String name, final KeyValue field, final Map<String, String> labels, final Map<String, String> tags,
                   final OpenMetricMetricType type, final String timestamp) {
        super(name, field, labels, tags, type, timestamp);
    }
}
