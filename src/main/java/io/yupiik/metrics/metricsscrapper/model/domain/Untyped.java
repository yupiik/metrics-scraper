package io.yupiik.metrics.metricsscrapper.model.domain;

import io.yupiik.metrics.metricsscrapper.model.metrics.OpenMetricMetricType;

import java.util.Map;

public class Untyped extends OpenMetric {
    public Untyped(String name, KeyValue field, Map<String, String> labels, Map<String, String> tags, OpenMetricMetricType type, String timestamp) {
        super(name, field, labels, tags, type, timestamp);
    }
}
