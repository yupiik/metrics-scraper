package io.yupiik.metrics.metricsscrapper.model.domain;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.metrics.metricsscrapper.model.metrics.OpenMetricMetricType;

import java.util.Map;

@JsonModel
public record Untyped(
        String help,
        String name,
        Map<String, String> tags,
        OpenMetricMetricType type,
        long timestamp,
        Double value
) {
}
