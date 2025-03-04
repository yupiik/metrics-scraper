package io.yupiik.metrics.metricsscrapper.model.http;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Status(
        int value,
        String payload
) {
}
