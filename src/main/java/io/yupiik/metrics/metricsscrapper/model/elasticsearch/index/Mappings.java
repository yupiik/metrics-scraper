package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.Map;

@JsonModel
public record Mappings(
        Map<String, Property> properties
) {
}
