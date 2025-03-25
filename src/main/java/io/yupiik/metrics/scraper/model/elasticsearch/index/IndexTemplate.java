package io.yupiik.metrics.scraper.model.elasticsearch.index;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;

import java.util.List;

@JsonModel
public record IndexTemplate(
        @JsonProperty("index_patterns")
        List<String> indexPatterns,

        Template template
) {
}
