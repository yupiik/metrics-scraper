package io.yupiik.metrics.scraper.model.elasticsearch.response;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record BulkResponseItemError(
        String type,
        String reason,
        String index_uuid,
        String shard,
        String index
) {
}
