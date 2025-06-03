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
package io.yupiik.metrics.scraper.model.domain;

import io.yupiik.metrics.scraper.model.elasticsearch.Document;

import java.util.Map;
import java.util.stream.Collectors;

public class Stat extends Document {
    private String timestamp;
    private String content;
    private Map<String, String> tags;

    public Stat(String timestamp, String content, Map<String, String> tags) {
        this.timestamp = timestamp;
        this.content = content;
        this.tags = tags;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    @Override
    public String json(){
        final StringBuilder json = new StringBuilder(content);
        if(tags != null && !tags.isEmpty()) {
            final var customTags = "\"tags\": {" + tags.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "},";
            json.insert(content.indexOf("{") + 1, customTags);
        }
        json.insert(content.indexOf("{") + 1, "\"@timestamp\": \"" + timestamp + "\", ");
        return json.toString();
    }
}
