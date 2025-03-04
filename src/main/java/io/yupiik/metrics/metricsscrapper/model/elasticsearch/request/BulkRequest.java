package io.yupiik.metrics.metricsscrapper.model.elasticsearch.request;

public record BulkRequest(
        String _index,
        String _id,
        Object document,
        BulkActionType actionType
) {
    public enum BulkActionType
    {
        index( "index", true ),
        delete( "delete", false ),
        create( "create", true ),
        update( "update", true );

        private final String code;
        private final boolean hasDocument;

        BulkActionType( String code, boolean hasDocument )
        {
            this.code = code;
            this.hasDocument = hasDocument;
        }

        public String getCode( )
        {
            return code;
        }

        public boolean hasDocument( )
        {
            return hasDocument;
        }
    }
}
