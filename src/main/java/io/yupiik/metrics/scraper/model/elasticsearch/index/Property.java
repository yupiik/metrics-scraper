package io.yupiik.metrics.scraper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Property(
        String type
) {
}
