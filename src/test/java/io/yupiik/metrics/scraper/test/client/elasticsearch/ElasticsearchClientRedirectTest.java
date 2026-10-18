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
package io.yupiik.metrics.scraper.test.client.elasticsearch;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.metrics.scraper.client.elasticsearch.ElasticsearchClient;
import io.yupiik.metrics.scraper.configuration.ElasticsearchClientConfiguration;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.configuration.ScraperHttpClientThreading;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.domain.Counter;
import io.yupiik.metrics.scraper.model.elasticsearch.request.BulkRequest;
import io.yupiik.metrics.scraper.model.http.Status$FusionJsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchClientRedirectTest {
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private SimpleHttpClient httpClient;
    private String base;
    private volatile boolean templateExists;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            final String call = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            calls.add(call);
            if (templateExists && call.startsWith("GET /_index_template/")) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.getResponseHeaders().add("Location", "https://elasticsearch.example.com" + exchange.getRequestURI());
                exchange.sendResponseHeaders(301, -1);
            }
            exchange.close();
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        httpClient.destroy(null);
        server.stop(0);
    }

    @Test
    void redirectIsAnError() {
        // before, the 301 on the template lookup was read as "template already exists"
        final ElasticsearchClient client = newClient();
        final ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.refresh(Counter.class).toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertTrue(error.getMessage().contains("Elasticsearch answered HTTP 301 on 'GET " + base + "/_index_template/metrics-scraper-counter'"), error.getMessage());
        assertTrue(error.getMessage().contains("redirects are not followed, check elasticsearch.base ('" + base + "')"), error.getMessage());
        assertEquals(List.of("GET /_index_template/metrics-scraper-counter"), calls); // redirect not followed
    }

    @Test
    void bulkRedirectIsAnError() {
        // before, the 301 replayed the _bulk POST as a GET without payload
        templateExists = true;
        final ElasticsearchClient client = newClient();
        final ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.bulk(List.of(new BulkRequest("metrics-scraper-counter", "1", null, BulkRequest.BulkActionType.delete)))
                        .toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertTrue(error.getMessage().contains("Elasticsearch answered HTTP 301 on 'POST " + base + "/_bulk' to 'https://elasticsearch.example.com/_bulk', " +
                "redirects are not followed, check elasticsearch.base ('" + base + "')"), error.getMessage());
        assertEquals(List.of("GET /_index_template/metrics-scraper-counter", "POST /_bulk"), calls);
    }

    private ElasticsearchClient newClient() {
        final MetricsScraperConfiguration configuration = new MetricsScraperConfiguration(
                null, List.of(), new ScraperHttpClientThreading(2, 4),
                new ElasticsearchClientConfiguration("yyyy-MM-dd", base, false, null, "metrics-scraper-", Map.of(), 5_000, 50_000_000));
        final JsonMapperImpl jsonMapper = new JsonMapperImpl(List.of(new Status$FusionJsonCodec()), Configuration.of(Map.of()));
        httpClient = new SimpleHttpClient(configuration, jsonMapper);
        return new ElasticsearchClient(configuration, jsonMapper, httpClient) {
            @Override
            public Stream<Class<?>> getIndexableClasses() {
                return Stream.of(Counter.class);
            }

            @Override
            public <T> List<BulkRequest> buildBulkRequest(final T instance, final Map<String, String> customTags) {
                return List.of();
            }
        };
    }
}
