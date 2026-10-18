/*
 * Copyright (c) 2025 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.metrics.scraper.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.testing.FusionSupport;
import io.yupiik.metrics.scraper.MetricsScraper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// starts the whole application on a fake scraping target and a fake elasticsearch:
// configuration binding -> scheduler -> scraping (with or without redirects) -> parsing -> _bulk indexing
@ExtendWith(FollowRedirectsTest.Servers.class) // must be before @FusionSupport to set the configuration before the container starts
@FusionSupport
class FollowRedirectsTest {
    @Test
    void followRedirectsPerScraper() throws InterruptedException {
        // default (followRedirects not set): the redirect is followed and the metric reaches elasticsearch
        await(() -> Servers.bulks.stream().anyMatch(it -> it.contains("followed_metric_total")));

        // followRedirects=false: the 302 is the response, the target is never called
        await(() -> Servers.warnings.stream().anyMatch(it -> it.contains("/not-followed Expected status 200 but got 302")));
        assertFalse(Servers.targetCalls.contains("/not-followed-target"), Servers.targetCalls::toString);
        assertTrue(Servers.bulks.stream().noneMatch(it -> it.contains("not_followed_metric_total")), Servers.bulks::toString);

        // elasticsearch calls never follow redirects, the init and _bulk went to elasticsearch only
        assertTrue(Servers.targetCalls.stream().noneMatch(it -> it.startsWith("/_")), Servers.targetCalls::toString);
    }

    private static void await(final BooleanSupplier condition) throws InterruptedException {
        final long end = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("Condition not met, target calls=" + Servers.targetCalls +
                        ", bulks=" + Servers.bulks + ", warnings=" + Servers.warnings);
            }
            Thread.sleep(50);
        }
    }

    public static class Servers implements BeforeAllCallback, AfterAllCallback {
        private static final List<String> targetCalls = new CopyOnWriteArrayList<>();
        private static final List<String> bulks = new CopyOnWriteArrayList<>();
        private static final List<String> warnings = new CopyOnWriteArrayList<>();

        private final Handler logs = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        };

        private HttpServer target;
        private HttpServer elasticsearch;
        private Map<String, String> properties;

        @Override
        public void beforeAll(final ExtensionContext context) throws IOException {
            target = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            target.createContext("/", exchange -> {
                final String path = exchange.getRequestURI().getPath();
                targetCalls.add(path);
                switch (path) {
                    case "/followed" -> redirect(exchange, "/followed-target");
                    case "/not-followed" -> redirect(exchange, "/not-followed-target");
                    case "/followed-target" -> respond(exchange, 200, "# TYPE followed_metric_total counter\nfollowed_metric_total 1\n");
                    case "/not-followed-target" -> respond(exchange, 200, "# TYPE not_followed_metric_total counter\nnot_followed_metric_total 1\n");
                    default -> respond(exchange, 404, "");
                }
            });
            target.start();

            elasticsearch = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            elasticsearch.createContext("/", exchange -> {
                final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (exchange.getRequestURI().getPath().equals("/_bulk")) {
                    bulks.add(body);
                    respond(exchange, 200, "{\"took\":1,\"errors\":false}");
                } else if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                } else { // cluster info, existing index template
                    respond(exchange, 200, "{}");
                }
            });
            elasticsearch.start();

            final String targetBase = "http://localhost:" + target.getAddress().getPort();
            properties = Map.ofEntries(
                    Map.entry("metrics-scraper.elasticsearch.base", "http://localhost:" + elasticsearch.getAddress().getPort()),
                    Map.entry("metrics-scraper.defaultScraping.interval", "200"),
                    Map.entry("metrics-scraper.scrapers.length", "2"),
                    Map.entry("metrics-scraper.scrapers.0.url", targetBase + "/followed"),
                    Map.entry("metrics-scraper.scrapers.0.scraping.interval", "200"),
                    Map.entry("metrics-scraper.scrapers.1.url", targetBase + "/not-followed"),
                    Map.entry("metrics-scraper.scrapers.1.followRedirects", "false"),
                    Map.entry("metrics-scraper.scrapers.1.scraping.interval", "200"));
            properties.forEach(System::setProperty);
            Logger.getLogger(MetricsScraper.class.getName()).addHandler(logs);
        }

        @Override
        public void afterAll(final ExtensionContext context) { // runs after the container is closed
            Logger.getLogger(MetricsScraper.class.getName()).removeHandler(logs);
            properties.keySet().forEach(System::clearProperty);
            target.stop(0);
            elasticsearch.stop(0);
        }

        private static void redirect(final HttpExchange exchange, final String location) throws IOException {
            exchange.getResponseHeaders().add("Location", location);
            respond(exchange, 302, "");
        }

        private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }
    }
}
