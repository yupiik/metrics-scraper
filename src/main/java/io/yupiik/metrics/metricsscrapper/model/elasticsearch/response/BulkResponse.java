package io.yupiik.metrics.metricsscrapper.model.elasticsearch.response;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.Map;

@JsonModel
public record BulkResponse(
        int took,
        boolean errors,
        Map<String, Object> items
) {
}
