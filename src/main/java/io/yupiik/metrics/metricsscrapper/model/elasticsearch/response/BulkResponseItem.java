package io.yupiik.metrics.metricsscrapper.model.elasticsearch.response;

public record BulkResponseItem(
        String _index,
        String _id,
        int status,
        BulkResponseItemError error
) {
}
