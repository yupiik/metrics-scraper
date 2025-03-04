package io.yupiik.metrics.metricsscrapper.model.elasticsearch;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Shards(
        long total,
        long failed,
        long successful
) {
}
