package io.yupiik.metrics.metricsscrapper.model.elasticsearch.response;

public record BulkResponseItemError(
        String type,
        String reason,
        String index_uuid,
        String shard,
        String index
) {
}
