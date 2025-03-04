package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record HttpTracing(
        @Property(value = "tracerConfiguration", documentation = "Tracing - Common Tracing configuration if enabled.")
        TracerConfiguration tracerConfiguration,

        @Property(value = "host", documentation = "Host - Tracing host (fake request) - .", defaultValue = "\"metrics-scrapper\"")
        String host,

        @Property(value = "port", documentation = "Port - Tracing port (fake request).", defaultValue = "-1")
        int port
) {
}
