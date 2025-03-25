package io.yupiik.metrics.scraper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record ScrapingConfiguration(
        @Property(value = "interval", documentation = "Interval - How often to scrape.", defaultValue = "60000L")
        long interval
) {
}
