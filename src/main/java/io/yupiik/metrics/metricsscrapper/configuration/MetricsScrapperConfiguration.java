package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.util.List;

// hosts the application configuration, you can add @Param as you need
// by default fusion makes it injectable directly in any bean
@RootConfiguration("metrics-scrapper") // will be the prefix of the system properties filtered to bind on the instance
public record MetricsScrapperConfiguration(
        @Property(value = "defaultScrapping", documentation = "Global Scrapping - Default when not set in a scrapper.")
        ScrappingConfiguration defaultScrapping,

        @Property(value="scrappers", documentation = "Scrappers - Where to scrap data from.")
        List<Scrapper> scrappers,

        @Property(value = "tracing", documentation = "Tracing - Tracing configuration if enabled (for HTTP client).")
        HttpTracing tracing,

        @Property(value = "threading", documentation = "Threading - Threading used by the asynchronouse HTTP client.")
        ScrapperHttpClientThreading threading,

        @Property(value = "elasticsearch", documentation = "Elasticsearch - Elasticsearch configuration to send metrics to Elasticsearch.")
        ElasticsearchClientConfiguration elasticsearch
) {
}
