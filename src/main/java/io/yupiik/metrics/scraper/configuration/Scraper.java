package io.yupiik.metrics.scraper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

import java.util.Map;

public record Scraper(
        @Property(value = "url", documentation = "The URL to scrape.", required = true)
        String url,

        @Property(value = "skipZero", documentation = "If set to true, zero will be ignored for counters. Depending the consumers, it can save memory and disk space.", defaultValue = "false")
        boolean skipZero,

        @Property(value = "headers", documentation = "Optional headers to set.")
        Map<String, String> headers,

        @Property(value = "mode", documentation = "Scraping - How to parse scraping response. Possible values are: ScrapingMode.PROMETHEUS (default)", defaultValue = "ScrapingMode.PROMETHEUS")
        ScrapingMode mode,

        @Property(value = "scraping", documentation = "Scraping - How often to poll data.", required = true)
        ScrapingConfiguration scraping,

        @Property(value = "expectedResponseCode", documentation = "Which response code is expected to be a success - failures are ignored.", defaultValue = "200")
        int expectedResponseCode,

        @Property(value = "timeout", documentation = "Timeout - How long a request can last.", defaultValue = "30000L")
        long timeout,

        @Property(value = "tags", documentation = "Tags to inject in elasticsearch if set.")
        Map<String, String> tags
) {
}
