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
package io.yupiik.metrics.scraper.http;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.common.ThreadFactoryImpl;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.model.http.Status;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

@ApplicationScoped
public class SimpleHttpClient {
    private final Logger log = Logger.getLogger(SimpleHttpClient.class.getName());

    private final MetricsScraperConfiguration configuration;
    private final JsonMapper jsonMapper;

    private ExecutorService pool;

    public SimpleHttpClient(final MetricsScraperConfiguration configuration, final JsonMapper jsonMapper) {
        log.fine("> Constructor SimpleHttpClient");
        this.configuration = configuration;
        this.jsonMapper = jsonMapper;
    }

    public void start(@OnEvent Start start) {
        log.info("> Initializing SimpleHttpClient");
        log.fine("> Configuration: " + configuration);
        this.init();
    }

    private void init() {
        this.pool = new ThreadPoolExecutor(
                this.configuration.threading().core(),
                this.configuration.threading().max(),
                1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(),
                new ThreadFactoryImpl("metrics-scraper-http-client"),
                (r, executor) -> {
                    log.severe(String.format("Rejecting http task '%s' from pool '%s'", r, executor));
                    throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + executor.toString());
                });
    }

    public void destroy(@OnEvent final Stop stop) {
        this.pool.shutdownNow();
        try {
            if (!this.pool.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warning("Async task not stopped in 2s (ES client)");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // if we import a real http client, let's change this method only
    public <T> CompletionStage<T> request(final String method, final String url, final Object payload,
                                          final int timeout, final boolean enableRedirects, final Class<T> type,
                                          final String... headers) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        try {
            log.fine(String.format("Requesting '%s' on '%s' with timeout %s on type %s and headers %s", url, method, timeout, type.getName(), Arrays.toString(headers)));
            if(this.pool == null){
                this.init();
            }
            this.pool.submit(() -> this.doRequest(method, url, payload, timeout, enableRedirects, result, headers, type));
        } catch (final Exception e) {
            log.severe("Could not submit request: " + e);
            result.completeExceptionally(e);
        }
        return result;
    }

    private <T> void doRequest(final String method, final String url, final Object payload,
                               final int timeout, final boolean enableRedirects, final CompletableFuture<T> result,
                               final String[] headers, final Class<T> returnType) {
        int responseCode = -1;
        try {
            final HttpURLConnection connection = HttpURLConnection.class.cast(URI.create(url).toURL().openConnection());
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(enableRedirects);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod(method);

            if (headers != null) {
                IntStream.range(0, headers.length / 2).forEach(idx -> connection.setRequestProperty(headers[idx * 2], headers[idx * 2 + 1]));
            }
            if (payload != null) {
                connection.setDoOutput(true);
                try (final OutputStream os = new BufferedOutputStream(connection.getOutputStream())) {
                    if (String.class.isInstance(payload)) {
                        os.write(String.valueOf(payload).getBytes(StandardCharsets.UTF_8));
                    } else {
                        os.write(jsonMapper.toString(payload).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            responseCode = connection.getResponseCode();
            if (Status.class == returnType) {
                log.fine("Complete result of type Status.class");
                result.complete(returnType.cast(new Status(responseCode, this.slurpPayload(connection))));
                return;
            }
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode > HttpURLConnection.HTTP_MULT_CHOICE) {
                log.severe(String.format("HTTP response to '%s' was a HTTP %s: '%s'", url, responseCode, this.slurpPayload(connection)));
                result.completeExceptionally(new IllegalArgumentException("HTTP response was HTTP " + responseCode));
                return;
            }
            this.onResponse(returnType, result, connection);
        } catch (final Exception e) {
            log.severe(e::getMessage);
            result.completeExceptionally(e);
        }
    }

    private <T> void onResponse(final Class<T> resultType, final CompletableFuture<T> result, final HttpURLConnection connection) throws IOException {
        if (resultType == String.class) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                result.complete(resultType.cast(reader.lines().collect(joining("\n"))));
            }
        } else {
            try (final InputStream is = connection.getInputStream()) {
                result.complete(jsonMapper.read(resultType, new InputStreamReader(is)));
            }
        }
    }

    private String slurpPayload(final HttpURLConnection connection) {

        log.fine(String.format("Slurping payload with url: '%s", connection.getURL()));
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8))) {
            final String lines = reader.lines().collect(joining("\n"));
            log.fine(String.format("Slurping lines: '%s", lines));
            return lines;
        } catch (final Exception e) {
            log.fine("Slurping lines: '-'");
            return "-";
        }
    }
}
