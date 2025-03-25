package io.yupiik.metrics.scraper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;

@JsonModel
public record ElasticsearchIndexSettingsIndex(
        @JsonProperty("number_of_shards")
        int shards,

        @JsonProperty("number_of_replicas")
        int replicas
) {
}
