package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record ScrappingConfiguration(
        @Property(value = "interval", documentation = "Interval - How often to scrape.", defaultValue = "60000L")
        long interval
) {
}
