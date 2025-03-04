package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Template(
        ElasticsearchIndexSettingsIndex settings,
        Mappings mappings
) {
}
