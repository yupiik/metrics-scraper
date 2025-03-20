package io.yupiik.metrics.metricsscrapper.model.elasticsearch.request;

import io.yupiik.metrics.metricsscrapper.model.domain.OpenMetric;

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
