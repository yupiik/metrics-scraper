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
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponse;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponse$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponseAction$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponseItem$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponseItemError$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.http.Status$FusionJsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// responses captured from a real Elasticsearch 8.18.4: "items" is an array of {"<action>": {item}}
class ElasticsearchBulkResponseTest {
    private static final String SUCCESS = "{\"errors\":false,\"took\":200,\"items\":[{\"index\":{\"_index\":\"probe-1\"," +
            "\"_id\":\"37OiraEB1z2x91gRbYRK\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0}," +
            "\"_seq_no\":0,\"_primary_term\":1,\"status\":201}}]}";
    private static final String ERROR = "{\"errors\":true,\"took\":0,\"items\":[{\"index\":{\"_index\":\"probe-err\"," +
            "\"_id\":\"4LOjraEB1z2x91gRjYQ6\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0}," +
            "\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"probe-err\",\"_id\":\"4bOjraEB1z2x91gRjYQ7\"," +
            "\"status\":400,\"error\":{\"type\":\"document_parsing_exception\",\"reason\":\"[1:6] failed to parse field [v] of type [long] " +
            "in document with id '4bOjraEB1z2x91gRjYQ7'. Preview of field's value: 'not-a-number'\",\"caused_by\":" +
            "{\"type\":\"illegal_argument_exception\",\"reason\":\"For input string: \\\"not-a-number\\\"\"}}}}]}";

    private HttpServer server;
    private SimpleHttpClient httpClient;
    private String base;
    private volatile String bulkResponse;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/_bulk")) {
                final byte[] bytes = bulkResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else { // template exists
                exchange.sendResponseHeaders(200, -1);
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
    void successItemsAreRead() throws Exception {
        bulkResponse = SUCCESS;
        final BulkResponse response = newClient().bulk(List.of(newRequest())).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, response.items().size());
        assertEquals(201, response.items().get(0).index().status());
        assertEquals("probe-1", response.items().get(0).item()._index());
    }

    @Test
    void errorsAreReported() throws Exception {
        bulkResponse = ERROR;
        final Object errors = newClient().doBulk(new Object(), Map.of()).get(10, TimeUnit.SECONDS);
        assertTrue(errors instanceof List<?> list && list.size() == 1 &&
                String.valueOf(list.get(0)).startsWith("document_parsing_exception: [1:6] failed to parse field [v] of type [long]"), String.valueOf(errors));
    }

    private static BulkRequest newRequest() {
        return new BulkRequest("metrics-scraper-counter", "1", null, BulkRequest.BulkActionType.delete);
    }

    private ElasticsearchClient newClient() {
        final MetricsScraperConfiguration configuration = new MetricsScraperConfiguration(
                null, List.of(), new ScraperHttpClientThreading(2, 4), null,
                new ElasticsearchClientConfiguration("yyyy-MM-dd", base, false, null, "metrics-scraper-", Map.of(), 5_000, 50_000_000));
        final JsonMapperImpl jsonMapper = new JsonMapperImpl(List.of(
                new Status$FusionJsonCodec(), new BulkResponse$FusionJsonCodec(), new BulkResponseAction$FusionJsonCodec(),
                new BulkResponseItem$FusionJsonCodec(), new BulkResponseItemError$FusionJsonCodec()), Configuration.of(Map.of()));
        httpClient = new SimpleHttpClient(configuration, jsonMapper);
        return new ElasticsearchClient(configuration, jsonMapper, httpClient) {
            @Override
            public Stream<Class<?>> getIndexableClasses() {
                return Stream.of(Counter.class);
            }

            @Override
            public <T> List<BulkRequest> buildBulkRequest(final T instance, final Map<String, String> customTags) {
                return List.of(newRequest());
            }
        };
    }
}
