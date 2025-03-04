package io.yupiik.metrics.metricsscrapper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record KubernetesDiscovery(
        @Property(value = "urlTemplate", documentation = "URL template - Same as scrapper URL but supporting ${authority} (host:port) placeholder to instantiate it per discovered instance.", defaultValue = "\"http://${authority}/meecrogate/api/metrics/prometheus?diffId=${configHashCode}\"")
        String urlTemplate,

        @Property(value = "selector", documentation = "Kubernetes selector.")
        String selector,

        @Property(value = "namespace", documentation = "Kubernetes namespace. If not set, global one will be used.")
        String namespace,

        @Property(value = "discoveryValidity", documentation = "Discovery Validity - How long discovery is considered valid. When this value is overpassed and a scrapping is launched, it will be refetched.", defaultValue = "60000L")
        long discoveryValidity
) {
}
