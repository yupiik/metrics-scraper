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
package io.yupiik.metrics.scraper.model.elasticsearch.request;

import io.yupiik.metrics.scraper.model.domain.OpenMetric;

public class BulkRequest {

    private final String _index;
    private final String _id;
    private final OpenMetric document;
    private final BulkActionType actionType;

    public BulkRequest(String _index, String _id, OpenMetric document, BulkActionType actionType) {
        this._index = _index;
        this._id = _id;
        this.document = document;
        this.actionType = actionType;
    }

    public String getIndex() {
        return _index;
    }

    public String getId() {
        return _id;
    }

    public OpenMetric getDocument() {
        return document;
    }

    public BulkActionType getActionType() {
        return actionType;
    }

    public enum BulkActionType {
        index("index", true),
        delete("delete", false),
        create("create", true),
        update("update", true);

        private final String code;
        private final boolean hasDocument;

        BulkActionType(String code, boolean hasDocument) {
            this.code = code;
            this.hasDocument = hasDocument;
        }

        public String getCode() {
            return code;
        }

        public boolean hasDocument() {
            return hasDocument;
        }
    }
}
