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
package io.yupiik.metrics.scraper.collector.elasticsearch;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.client.elasticsearch.ElasticsearchClient;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.domain.Stat;
import io.yupiik.metrics.scraper.model.elasticsearch.request.BulkRequest;
import io.yupiik.metrics.scraper.model.statistics.ScraperStatistics;
import io.yupiik.metrics.scraper.model.statistics.Statistics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.TimeUnit.MINUTES;

@ApplicationScoped
public class ElasticsearchStatsCollector{
    private final Logger log = Logger.getLogger(ElasticsearchStatsCollector.class.getName());

    private ElasticsearchClient esClient;
    private SimpleHttpClient httpClient;
    private JsonMapper jsonMapper;
    private MetricsScraperConfiguration configuration;

    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final Map<CompletableFuture<?>, Boolean> pending = new ConcurrentHashMap<>();

    public ElasticsearchStatsCollector(final MetricsScraperConfiguration configuration, final SimpleHttpClient httpClient, final JsonMapper jsonMapper) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
    }

    public void start(@OnEvent final Start start) {
        log.info("> Initializing " + getClass().getSimpleName());
        this.esClient = new ElasticsearchClient(this.configuration, this.jsonMapper, this.httpClient ) {
            @Override
            public Stream<Class<?>> getIndexableClasses() {
                return Stream.of(Stat.class);
            }

            @Override
            public <T> List<BulkRequest> buildBulkRequest(T instance, Map<String, String> customTags) {
                final var statistics = Statistics.class.cast(instance);
                final var stat = new Stat(this.toUTCTimeStamp(statistics.getTimestamp()), statistics.getContent(), customTags);
                return List.of(new BulkRequest(currentIndex.apply(stat.getClass()), null, stat, BulkRequest.BulkActionType.index));
            }
        };
    }

    public void stop(@OnEvent final Stop stop) {
        log.info("> Closing " + getClass().getSimpleName());
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


    public void onScraping(@OnEvent final ScraperStatistics scraperStats) {
        log.fine("> onStatistics");
        final Statistics statistics = scraperStats.getStatistics();
        if (closing.get() || statistics == null) {
            log.fine("> empty statistics or closed collector");
            return;
        }
        final CompletableFuture<?> promise = esClient.doBulk(statistics, scraperStats.getConfiguration().tags());
        pending.put(promise, true);
        promise.handle((r, e) -> {
            if (e != null) {
                log.log(Level.SEVERE, e.getMessage() + ", sending statistics: " + statistics, e);
            } else {
                log.fine(String.format("Success sending statistics %s", statistics));
            }
            pending.remove(promise);
            return r;
        });
    }
}
