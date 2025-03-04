package io.yupiik.metrics.metricsscrapper.model.elasticsearch;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;

@JsonModel
public record DocumentResult(
        String result, // "created"

        @JsonProperty("_id")
        String id,

        @JsonProperty("_index")
        String index,

        @JsonProperty("_type")
        String type, // _doc

        @JsonProperty("_version")
        int version, // internal meta field, not ours

        @JsonProperty("_shards")
        Shards shards
) {
}
