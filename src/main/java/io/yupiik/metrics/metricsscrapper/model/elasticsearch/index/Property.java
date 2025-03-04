package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Property(
        String name,
        String coucou
) {
}
