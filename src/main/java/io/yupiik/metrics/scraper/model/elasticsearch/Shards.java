package io.yupiik.metrics.scraper.model.elasticsearch;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Shards(
        long total,
        long failed,
        long successful
) {
}
