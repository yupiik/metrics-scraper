/*
 * Copyright (c) 2025 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.metrics.scraper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.Map;

@JsonModel
public record ElasticsearchClientConfiguration(
        @Property(value = "indexNameSuffix", documentation = "Index Name Suffix - An optional (but recommended) date pattern suffix to append to index names", defaultValue = "\"ISO_LOCAL_DATE\"")
        String indexNameSuffix,

        @Property(value = "base", documentation = "Base - Elasticsearch base, using a local instance it is generally http://localhost:9200.")
        String base,

        @Property(value = "refreshOnWrite", documentation = "Refresh On Write -  Should updates trigger a refresh. This setting enables to reduce the update latency but also stresses more the Elasticserach backend. It is recommended to tune your ELasticsearch instance rather than setting it to true - except for dev and demo instances.", defaultValue = "false")
        boolean refreshOnWrite,

        @Property(value = "settingsTemplate", documentation = "Settings Template - Elasticsearch basic settings used when createSchema is true and portal creates the index mappings.")
        ElasticsearchIndexSettings settingsTemplate,

        @Property(value = "indexPrefix", documentation = "Index Prefix - Prefix for indices for all tables.", defaultValue = "\"metrics-scraper-\"")
        String indexPrefix,

        @Property(value = "headers", documentation = "Headers - Headers to set when calling elasticsearch, this is primarly intended for security headers.")
        Map<String, String> headers,

        @Property(value = "timeout", documentation = "Timeout - HTTP connection and read timeout", defaultValue = "60000L")
        long timeout
) {

    @JsonModel
    public record ElasticsearchIndexSettings(
            @Property(value = "index", documentation = "Index - Index settings for created indices")
            ElasticsearchIndexSettingsIndex index
    ) {

    }

    @JsonModel
    public record ElasticsearchIndexSettingsIndex(
            @Property(value = "shards", documentation = "Elasticsearch Settings - The number of primary shards that an index should have.", defaultValue = "3")
            int shards,

            @Property(value = "replicas", documentation = "Elasticsearch Settings - The number of replicas each primary shard has.", defaultValue = "2")
            int replicas
    ) {

    }
}
