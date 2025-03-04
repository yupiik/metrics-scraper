package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

import java.util.Map;

public record Scrapper(
        @Property(value = "url", documentation = "The URL to scrape.", required = true)
        String url,

        @Property(value = "skipZero", documentation = "If set to true, zero will be ignored for counters. Depending the consumers, it can save memory and disk space.", defaultValue = "false")
        boolean skipZero,

        @Property(value = "headers", documentation = "Optional headers to set.")
        Map<String, String> headers,

        @Property(value = "kubernetesDiscovery", documentation = "Kubernetes Discovery - How to discover URL from a kubernetes selector.")
        KubernetesDiscovery kubernetesDiscovery,

        @Property(value = "mode", documentation = "Scrapping - How to parse scrapping response. Possible values are: ScrappingMode.PROMETHEUS (default)", defaultValue = "ScrappingMode.PROMETHEUS")
        ScrappingMode mode,

        @Property(value = "scrapping", documentation = "Scrapping - How often to poll data.", required = true)
        ScrappingConfiguration scrapping,

        @Property(value = "expectedResponseCode", documentation = "Which response code is expected to be a success - failures are ignored.", defaultValue = "200")
        int expectedResponseCode,

        @Property(value = "timeout", documentation = "Timeout - How long a request can last.", defaultValue = "30000L")
        long timeout,

        @Property(value = "tags", documentation = "Tags to inject in elasticsearch if set.")
        Map<String, String> tags
) {
}
