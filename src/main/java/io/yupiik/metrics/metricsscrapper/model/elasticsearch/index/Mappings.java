package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;

@JsonModel
public record Mappings(
        List<Property> properties
) {
}
