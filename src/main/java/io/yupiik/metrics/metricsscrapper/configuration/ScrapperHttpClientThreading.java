package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record ScrapperHttpClientThreading(
        @Property(value = "core", documentation = "Core Threads - How many core threads are used to complete HTTP asynchronous requests.", defaultValue = "32")
        int core,

        @Property(value = "max", documentation = "Max Threads - How many max threads are used to complete HTTP asynchronous requests.", defaultValue = "128")
        int max
) {
}
