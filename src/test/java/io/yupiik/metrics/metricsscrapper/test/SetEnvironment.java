package io.yupiik.metrics.metricsscrapper.test;

import org.junit.jupiter.api.extension.Extension;

public class SetEnvironment implements Extension {
    static {
        // You can configure your test environment there, it is setup before CDI container starts

        // use a random tomcat port for tests using our specific configuration, fusion.http-server.port would work too
        System.setProperty("java.util.logging.manager", "io.yupiik.logging.jul.YupiikLogManager");
        System.setProperty("metrics-scrapper.threading.core", "32");
        System.setProperty("metrics-scrapper.threading.max", "128");
        System.setProperty("metrics-scrapper.scrappers.length", "1");
        System.setProperty("metrics-scrapper.scrappers.0.url", "http://localhost:8080/metrics");
        System.setProperty("metrics-scrapper.scrappers.0.scrapping.interval", "1000");
        System.setProperty("metrics-scrapper.elasticsearch.base", "http://localhost:9200");
        System.setProperty("metrics-scrapper.elasticsearch.indexPrefix", "test-scrapper-");
    }
}
