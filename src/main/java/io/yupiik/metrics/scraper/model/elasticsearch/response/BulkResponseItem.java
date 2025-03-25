package io.yupiik.metrics.scraper.model.elasticsearch.response;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record BulkResponseItem(
        String _index,
        String _id,
        int status,
        BulkResponseItemError error
) {
}
