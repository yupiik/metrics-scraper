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
package io.yupiik.metrics.scraper;

import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.configuration.Scraper;
import io.yupiik.metrics.scraper.configuration.ScrapingConfiguration;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.http.Status;
import io.yupiik.metrics.scraper.model.metrics.Metrics;
import io.yupiik.metrics.scraper.model.metrics.ScraperMetrics;
import io.yupiik.metrics.scraper.model.statistics.ScraperStatistics;
import io.yupiik.metrics.scraper.model.statistics.Statistics;
import io.yupiik.metrics.scraper.protocol.OpenMetricsReader;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class MetricsScraper {

    private static final Logger log = Logger.getLogger(MetricsScraper.class.getName());

    private final MetricsScraperConfiguration configuration;
    private final OpenMetricsReader openMetricsReader;
    private final SimpleHttpClient httpClient;
    private final Emitter emitter;
    private ScheduledExecutorService executor;

    public MetricsScraper(final MetricsScraperConfiguration configuration, final OpenMetricsReader openMetricsReader,
                          final SimpleHttpClient httpClient, final Emitter emitter) {
        this.configuration = configuration;
        this.openMetricsReader = openMetricsReader;
        this.httpClient = httpClient;
        this.emitter = emitter;
    }

    public void start(@OnEvent Start start){
        log.info("> Initializing MetricsScraper");
        log.info("> Configuration: " + configuration);
        this.executor = Executors.newScheduledThreadPool(10); //TODO add threading configuration with defaults
        final ScrapingConfiguration defaultScraping = configuration.defaultScraping();
        if (configuration.scrapers() == null || configuration.scrapers().isEmpty()) {
            log.info("No scrapers defined, exiting");
            return;
        }

        final Map<String, String> configHeader = ofNullable(configuration.elasticsearch().headers()).orElseGet(HashMap::new);
        final String[] headers = Stream.concat(
                        Stream.concat(
                                !configHeader.containsKey("Content-Type") ? Stream.of("Content-Type", "application/json") : Stream.empty(),
                                !configHeader.containsKey("Accept") ? Stream.of("Accept", "application/json") : Stream.empty()),
                        configHeader.entrySet().stream()
                                .flatMap(it -> Stream.of(it.getKey(), it.getValue())))
                .toArray(String[]::new);
        final int timeout = (int) Math.max(0, configuration.elasticsearch().timeout());
        final String base = configuration.elasticsearch().base();
        this.httpClient.request("GET", base, null, timeout, true, Status.class, headers)
                .handle((r, error) -> {
                    log.fine(String.format("Got response from ES '%s'", r));
                    return r;
                });
        configuration.scrapers().forEach(scraper -> this.launchScraping(scraper, defaultScraping, httpClient, openMetricsReader, emitter));
    }

    private void launchScraping(final Scraper scraper, final ScrapingConfiguration defaultScraping, final SimpleHttpClient http,
                                final OpenMetricsReader openMetricsReader, final Emitter emitter) {
        if (scraper.url() == null) {
            log.info(String.format("Scraper %s has no url, skipping", scraper));
            return;
        }
        final ScrapingConfiguration scraping = ofNullable(scraper.scraping()).orElse(defaultScraping);
        if (scraping.interval() <= 0) {
            log.info(String.format("Skipping scraper %s since interval is <= 0 (%s)", scraper, scraping.interval()));
            return;
        }
        log.info(String.format("Scheduling scraping for %s", scraper));
        final ScraperRuntime runtime = new ScraperRuntime(http, scraper, openMetricsReader, emitter);
        this.executor.scheduleAtFixedRate(runtime, 0, scraping.interval(), TimeUnit.MILLISECONDS);
    }

    private static class ScraperRuntime implements Runnable {
        private final SimpleHttpClient http;
        private final OpenMetricsReader openMetricsReader;
        private final Scraper config;

        private final int timeout;
        private final String[] headers;
        private final AtomicBoolean calling = new AtomicBoolean();
        private final Emitter emitter;

        private ScraperRuntime(final SimpleHttpClient http, final Scraper config, final OpenMetricsReader openMetricsReader, final Emitter emitter) {
            this.http = http;
            this.config = config;
            this.openMetricsReader = openMetricsReader;

            this.emitter = emitter;
            this.timeout = (int) Math.max(0, config.timeout());
            this.headers = config.headers() == null ? new String[0] : config.headers().entrySet().stream()
                    .flatMap(it -> Stream.of(it.getKey(), it.getValue()))
                    .toArray(String[]::new);
        }

        @Override
        public void run() {
            if (!calling.compareAndSet(false, true)) {
                log.fine(String.format("Skipping scraping of '%s'", config));
                return;
            }
            this.doScraping(config.url());
        }

        private void doScraping(final String url) {
            log.fine(String.format("Scraping '%s' on '%s'", config, url));
            try {
                http.request("GET", url, null, timeout, true, Status.class, headers).handle((r, error) -> {
                    try {
                        log.fine(String.format("Got scraping of '%s'", r));
                        if (error != null) {
                            log.log(Level.SEVERE, error.getMessage(), error);
                        } else if (r.value() != config.expectedResponseCode()) {
                            if (log.isLoggable(Level.FINE)) {
                                log.warning(String.format("Expected status %s but got %s (%s)", config.expectedResponseCode(), r.value(), r.payload()));
                            } else {
                                log.warning(String.format("Expected status %s but got %s", config.expectedResponseCode(), r.value()));
                            }
                        } else if (!r.payload().trim().isEmpty()) {
                            log.fine(String.format("Open metrics read: %s", r.payload()));
                            this.onResponse(r.payload());
                        } else {
                            log.fine(String.format("Got an empty payload from %s", url));
                        }
                    } finally {
                        calling.set(false);
                    }
                    return r;
                });
            } catch (final RuntimeException re) { // don't break scheduler for that
                log.log(Level.SEVERE, re.getMessage(), re);
                calling.set(false);
            }
        }

        private void onResponse(final String payload) {
            final long timestamp = Instant.now().toEpochMilli();
            switch (config.mode()){
                case PROMETHEUS -> {
                    final Metrics metrics = openMetricsReader.read(payload, timestamp);
                    if (config.skipZero()) {
                        metrics.dropZeroCounters();
                    }
                    if (!metrics.isEmpty()) {
                        log.fine(String.format("Emitting metrics %s", metrics));
                        this.emitter.emit(new ScraperMetrics(timestamp, config, metrics));
                    } else {
                        log.fine(String.format("No metrics found in %s", payload));
                    }
                }

                case ELASTICSEARCH -> {
                    final var stat = new Statistics(timestamp, payload);
                    log.fine(String.format("Emitting metrics %s", stat));
                    this.emitter.emit(new ScraperStatistics(timestamp, config, stat));
                }

                default -> throw new IllegalStateException(config.mode().toString());
            }
        }
    }

    public void onStop(@OnEvent final Stop stop) {
        log.info("> Stop: " + stop);
        this.executor.shutdownNow();
    }
}
