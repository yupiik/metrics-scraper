package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record TracerConfiguration(
        @Property(value = "parentSpanHeader", documentation = "Parent Span Header - Name of the header holding the parent span identifier.", defaultValue = "\"X-B3-ParentSpanId\"")
        String parentSpanHeader,
        @Property(value = "spanHeader", documentation = "Span Header - Name of the header holding the incoming span identifier.", defaultValue = "\"X-B3-SpanId\"")
        String spanHeader,
        @Property(value = "traceHeader", documentation = "Trace Header - Name of the header holding the trace identifier.", defaultValue = "\"X-B3-TraceId\"")
        String traceHeader,
        @Property(value = "baggageHeaderNamePrefix", documentation = "Baggage Headers - Prefix of header names holding baggages (propagated tags).")
        String baggageHeaderNamePrefix,
        @Property(value = "serviceName", documentation = "Service - Service name for this instance.", defaultValue = "\"metric-scrapper\"")
        String serviceName,
        @Property(value = "component", documentation = "Component - Component name - put in tags.", defaultValue = "\"metric-scrapper\"")
        String component,
        @Property(value = "idGeneratorType", documentation = "ID Generator - Trace/Span identifier generator toto.")
        IdGeneratorType idGeneratorType,
        @Property(value = "collectionType", documentation = "Collector - Type of the collector.")
        TracingCollectionType collectionType,
        @Property(value = "tracingLogCollectionLoggerName", documentation = "Logger Name - For collectionType==LOG, the logger name.", defaultValue = "\"io.yupiik.metrics-scrapper.tracing\"")
        String tracingLogCollectionLoggerName,
        @Property(value = "tracingLogCollectionBulkSize", documentation = "Logger Flush Size - For collectionType==LOG, how many messages to bulk before logging.", defaultValue = "100")
        int tracingLogCollectionBulkSize,
        @Property(value = "tracingLogCollectionTimeout", documentation = "Logger Timeout - For collectionType==LOG, how many time to wait before sending the spans if there is no message arriving.", defaultValue = "60000")
        int tracingLogCollectionTimeout,
        @Property(value = "zipkinClientName", documentation = "Client Name - For collectionType==HTTP, the zipkin client reference in the global configuration.", defaultValue = "\"default\"")
        String zipkinClientName
) {
    public enum IdGeneratorType {
        COUNTER, UUID, HEX
    }

    public enum TracingCollectionType {
        LOG, HTTP
    }
}
