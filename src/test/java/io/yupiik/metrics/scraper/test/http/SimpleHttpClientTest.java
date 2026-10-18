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
package io.yupiik.metrics.scraper.test.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.configuration.ScraperHttpClientThreading;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.elasticsearch.Shards;
import io.yupiik.metrics.scraper.model.elasticsearch.Shards$FusionJsonCodec;
import io.yupiik.metrics.scraper.model.http.Status;
import io.yupiik.metrics.scraper.model.http.Status$FusionJsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleHttpClientTest {
    private HttpServer server;
    private SimpleHttpClient client;
    private String base;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
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

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/echo", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final String header = exchange.getRequestHeaders().getFirst("X-Test");
            respond(exchange, 200, exchange.getRequestMethod() + "|" + header + "|" + body);
        });
        server.createContext("/shards", exchange -> respond(exchange, 200, "{\"total\":3,\"failed\":1,\"successful\":2}"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "{\"error\":\"not found\"}"));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/echo");
            respond(exchange, 302, "");
        });
        server.createContext("/cross-host", exchange -> { // localhost -> 127.0.0.1
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/echo");
            respond(exchange, 302, "");
        });
        server.createContext("/head", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "late");
        });
        Logger.getLogger(SimpleHttpClient.class.getName()).addHandler(logs);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();

        client = new SimpleHttpClient(
                new MetricsScraperConfiguration(null, List.of(), new ScraperHttpClientThreading(2, 4), null),
                new JsonMapperImpl(List.of(new Status$FusionJsonCodec(), new Shards$FusionJsonCodec()), Configuration.of(Map.of())));
    }

    @AfterEach
    void stop() {
        Logger.getLogger(SimpleHttpClient.class.getName()).removeHandler(logs);
        client.destroy(null);
        server.stop(0);
    }

    @Test
    void getAsString() throws Exception {
        assertEquals("GET|null|", get(client.request("GET", base + "/echo", null, 5_000, true, String.class)));
    }

    @Test
    void headers() throws Exception {
        assertEquals("GET|value|", get(client.request("GET", base + "/echo", null, 5_000, true, String.class, "X-Test", "value")));
    }

    @Test
    void stringPayloadIsSentAsIs() throws Exception {
        assertEquals("POST|null|{\"raw\":true}", get(client.request("POST", base + "/echo", "{\"raw\":true}", 5_000, true, String.class)));
    }

    @Test
    void objectPayloadIsSerializedAsJson() throws Exception {
        assertEquals(
                "PUT|null|{\"failed\":1,\"successful\":2,\"total\":3}",
                get(client.request("PUT", base + "/echo", new Shards(3, 1, 2), 5_000, true, String.class)));
    }

    @Test
    void jsonResponseIsDeserialized() throws Exception {
        assertEquals(new Shards(3, 1, 2), get(client.request("GET", base + "/shards", null, 5_000, true, Shards.class)));
    }

    @Test
    void statusDoesNotFailOnErrorAndReadsErrorBody() throws Exception {
        assertEquals(new Status(404, "{\"error\":\"not found\"}"), get(client.request("GET", base + "/missing", null, 5_000, true, Status.class)));
    }

    @Test
    void errorFailsForNonStatusType() {
        final ExecutionException error = assertThrows(ExecutionException.class,
                () -> get(client.request("GET", base + "/missing", null, 5_000, true, String.class)));
        assertEquals("HTTP response was HTTP 404", error.getCause().getMessage());
    }

    @Test
    void head() throws Exception {
        assertEquals(200, get(client.request("HEAD", base + "/head", null, 5_000, true, Status.class)).value());
    }

    @Test
    void followRedirects() throws Exception {
        assertEquals("GET|null|", get(client.request("GET", base + "/redirect", null, 5_000, true, String.class)));
        assertEquals(List.of(), warnings); // same host
    }

    @Test
    void crossHostRedirectWarnsOnce() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertEquals("GET|value|", get(client.request("GET", base + "/cross-host", null, 5_000, true, String.class, "X-Test", "value")));
        }
        assertEquals(List.of("'" + base + "/cross-host' redirected to another host ('http://127.0.0.1:" + server.getAddress().getPort() + "/echo'), " +
                "custom headers were sent to it (Authorization was not), use the final URL or disable followRedirects if it is not expected"), warnings);
    }

    @Test
    void doNotFollowRedirects() throws Exception {
        assertEquals(302, get(client.request("GET", base + "/redirect", null, 5_000, false, Status.class)).value());
    }

    @Test
    void notFollowedRedirectFailsForNonStatusType() {
        final ExecutionException error = assertThrows(ExecutionException.class,
                () -> get(client.request("GET", base + "/redirect", null, 5_000, false, String.class)));
        final SimpleHttpClient.RedirectNotFollowedException redirect = assertInstanceOf(SimpleHttpClient.RedirectNotFollowedException.class, error.getCause());
        assertEquals("HTTP response was HTTP 302 (redirect to '" + base + "/echo' not followed)", redirect.getMessage());
        assertEquals(302, redirect.getStatus());
        assertEquals(base + "/echo", redirect.getLocation());
    }

    @Test
    void timeout() {
        assertThrows(ExecutionException.class, () -> get(client.request("GET", base + "/slow", null, 200, true, String.class)));
    }

    private static <T> T get(final java.util.concurrent.CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
