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

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.metrics.scraper.MetricsScraper;
import io.yupiik.metrics.scraper.configuration.ElasticsearchClientConfiguration;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.configuration.Scraper;
import io.yupiik.metrics.scraper.configuration.ScraperHttpClientThreading;
import io.yupiik.metrics.scraper.configuration.ScrapingConfiguration;
import io.yupiik.metrics.scraper.configuration.ScrapingMode;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.http.Status$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.metrics.ScraperMetrics;
import io.yupiik.metrics.scraper.protocol.OpenMetricsReader;
import io.yupiik.metrics.scraper.protocol.StatisticsReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the endpoint answers slowly so the stop happens while a scraping is in flight
class MetricsScraperStopTest {
    private final CountDownLatch called = new CountDownLatch(1);
    private final List<Object> emitted = new CopyOnWriteArrayList<>();
    private final AtomicInteger fastCalls = new AtomicInteger();
    private HttpServer server;
    private SimpleHttpClient httpClient;
    private String url;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/metrics", exchange -> {
            called.countDown();
            try {
                Thread.sleep(1_000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final byte[] bytes = "# TYPE slow_total counter\nslow_total 1\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/fast", exchange -> {
            fastCalls.incrementAndGet();
            final byte[] bytes = "# TYPE fast_total counter\nfast_total 1\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort() + "/metrics";
    }

    @AfterEach
    void stop() {
        httpClient.destroy(null);
        server.stop(0);
    }

    @Test
    void inFlightScrapingFinishesWithinGracePeriod() throws Exception {
        final MetricsScraper scraper = newScraper(5_000);
        assertTrue(called.await(5, TimeUnit.SECONDS));
        scraper.onStop(null); // waits for the in-flight scraping
        assertEquals(1, emitted.size(), emitted::toString);
        assertTrue(emitted.get(0) instanceof ScraperMetrics, emitted::toString);
    }

    @Test
    void inFlightScrapingIsCancelledAfterGracePeriod() throws Exception {
        final MetricsScraper scraper = newScraper(100);
        assertTrue(called.await(5, TimeUnit.SECONDS));
        final long start = System.nanoTime();
        scraper.onStop(null);
        final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(duration < 900, () -> "stop took " + duration + "ms");
        scraper.onStop(null); // idempotent
    }

    @Test
    void stopDuringStartupWaitsForIt() throws Exception {
        // the startup is paused after scheduling the first scraper, while the stop is received
        final CountDownLatch startupPaused = new CountDownLatch(1);
        final CountDownLatch resumeStartup = new CountDownLatch(1);
        final String fast = url.replace("/metrics", "/fast");
        final List<Scraper> delegate = List.of(newScraperConfiguration(fast + "?1", 100), newScraperConfiguration(fast + "?2", 100));
        final List<Scraper> scrapers = new AbstractList<>() {
            @Override
            public Scraper get(final int index) {
                return delegate.get(index);
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override // MetricsScraper.start() schedules the scrapers with forEach
            public void forEach(final Consumer<? super Scraper> action) {
                action.accept(delegate.get(0));
                startupPaused.countDown();
                try {
                    resumeStartup.await(5, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                action.accept(delegate.get(1));
            }
        };
        final MetricsScraper scraper = newScraper(scrapers, 5_000, false);

        final AtomicLong startupEnd = new AtomicLong();
        final AtomicLong stopEnd = new AtomicLong();
        final CompletableFuture<Void> starting = CompletableFuture.runAsync(() -> {
            scraper.start(null);
            startupEnd.set(System.nanoTime());
        });
        assertTrue(startupPaused.await(5, TimeUnit.SECONDS));
        final CompletableFuture<Void> stopping = CompletableFuture.runAsync(() -> {
            scraper.onStop(null);
            stopEnd.set(System.nanoTime());
        });
        Thread.sleep(300); // lets the stop run if nothing prevents it
        resumeStartup.countDown();

        starting.get(5, TimeUnit.SECONDS); // the startup must not fail (scheduling on a stopped executor)
        stopping.get(10, TimeUnit.SECONDS);
        assertTrue(stopEnd.get() > startupEnd.get(), "the stop must wait for the startup");

        // the stop really stopped everything: no scraping anymore (interval is 100ms)
        Thread.sleep(200);
        final int calls = fastCalls.get();
        Thread.sleep(500);
        assertEquals(calls, fastCalls.get(), "scrapings continued after the stop");
    }

    private static Scraper newScraperConfiguration(final String url, final long interval) {
        return new Scraper(url, false, null, true, ScrapingMode.PROMETHEUS, new ScrapingConfiguration(interval), 200, 5_000, null);
    }

    private MetricsScraper newScraper(final long gracePeriod) {
        return newScraper(List.of(new Scraper(url, false, null, true, ScrapingMode.PROMETHEUS, null, 200, 5_000, null)), gracePeriod, true);
    }

    private MetricsScraper newScraper(final List<Scraper> scrapers, final long gracePeriod, final boolean start) {
        final MetricsScraperConfiguration configuration = new MetricsScraperConfiguration(
                new ScrapingConfiguration(60_000),
                scrapers,
                new ScraperHttpClientThreading(2, 4), null, gracePeriod,
                new ElasticsearchClientConfiguration("yyyy-MM-dd", url.replace("/metrics", ""), false, null, "metrics-scraper-", Map.of(), 5_000, 50_000_000));
        final JsonMapperImpl jsonMapper = new JsonMapperImpl(List.of(new Status$FusionJsonCodec()), Configuration.of(Map.of()));
        httpClient = new SimpleHttpClient(configuration, jsonMapper);
        final Emitter emitter = new Emitter() {
            @Override
            public <T> void emit(final T event) {
                emitted.add(event);
            }
        };
        final MetricsScraper scraper = new MetricsScraper(configuration, new OpenMetricsReader(), new StatisticsReader(jsonMapper), httpClient, emitter);
        if (start) {
            scraper.start(null);
        }
        return scraper;
    }
}
