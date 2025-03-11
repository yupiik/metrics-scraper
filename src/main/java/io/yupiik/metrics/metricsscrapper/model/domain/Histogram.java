package io.yupiik.metrics.metricsscrapper.model.domain;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;
import io.yupiik.metrics.metricsscrapper.model.metrics.OpenMetricMetricType;

import java.util.Map;

@JsonModel
public record Histogram(
        String help,
        String name,
        Map<String, String> tags,
        OpenMetricMetricType type,
        @JsonProperty("@timestamp")
        String timestamp,
        Double sum,
        Double count,
        Map<String, Double> buckets,
        Double min,
        Double max,
        Double mean,
        Double stddev
) {
}
