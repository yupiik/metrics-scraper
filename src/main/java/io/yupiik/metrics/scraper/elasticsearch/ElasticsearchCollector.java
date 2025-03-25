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
package io.yupiik.metrics.scraper.elasticsearch;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.metrics.scraper.model.metrics.Metrics;
import io.yupiik.metrics.scraper.model.metrics.ScraperMetrics;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.TimeUnit.MINUTES;

@ApplicationScoped
public class ElasticsearchCollector {
    private final Logger log = Logger.getLogger(ElasticsearchCollector.class.getName());

    private final ElasticsearchClient esClient;

    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final Map<CompletableFuture<?>, Boolean> pending = new ConcurrentHashMap<>();

    public ElasticsearchCollector(final ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public void start(@OnEvent final Start start) {
        log.info("> Initializing ElasticsearchCollector");
    }

    public void stop(@OnEvent final Stop stop) {
        log.info("> Closing ElasticsearchCollector");
        closing.set(true);
        final CompletableFuture<?>[] futures = pending.keySet().toArray(new CompletableFuture<?>[0]);
        try { // try to flush it a bit but not that critical
            allOf(futures).get(1, MINUTES);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException | TimeoutException e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public void onMetrics(@OnEvent final ScraperMetrics scraperMetrics) {
        log.fine("> onMetrics");
        final Metrics metrics = scraperMetrics.getMetrics();
        if (closing.get() || metrics.isEmpty()) {
            log.fine("> empty metrics or closed collector");
            return;
        }
        final CompletableFuture<?> promise = esClient.createBulk(metrics, scraperMetrics.getConfiguration().tags(), scraperMetrics.getTimestamp());
        pending.put(promise, true);
        promise.handle((r, e) -> {
            if (e != null) {
                log.log(Level.SEVERE, e.getMessage() + ", sending metrics: " + metrics, e);
            } else {
                log.fine(String.format("Success sending metrics %s", metrics));
            }
            pending.remove(promise);
            return r;
        });
    }
}
