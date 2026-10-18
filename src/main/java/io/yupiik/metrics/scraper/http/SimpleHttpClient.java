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
import io.yupiik.fusion.httpclient.core.ExtendedHttpClient;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClientConfiguration;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.common.ThreadFactoryImpl;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.model.http.Status;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

@ApplicationScoped
public class SimpleHttpClient {
    private final Logger log = Logger.getLogger(SimpleHttpClient.class.getName());

    private final MetricsScraperConfiguration configuration;
    private final JsonMapper jsonMapper;

    private final Set<String> warnedCrossHostRedirects = ConcurrentHashMap.newKeySet();

    private ExecutorService pool;
    private final List<HttpClient> delegates = new ArrayList<>();
    private ExtendedHttpClient redirectingClient;
    private ExtendedHttpClient nonRedirectingClient;

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

    private synchronized void init() {
        if (this.pool != null) {
            return;
        }
        this.pool = new ThreadPoolExecutor(
                this.configuration.threading().core(),
                this.configuration.threading().max(),
                1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(),
                new ThreadFactoryImpl("metrics-scraper-http-client"),
                (r, executor) -> {
                    log.severe(String.format("Rejecting http task '%s' from pool '%s'", r, executor));
                    throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + executor.toString());
                });
        // redirect policy is set per client in java.net.http so keep one client per policy
        this.redirectingClient = this.newClient(HttpClient.Redirect.NORMAL);
        this.nonRedirectingClient = this.newClient(HttpClient.Redirect.NEVER);
    }

    private ExtendedHttpClient newClient(final HttpClient.Redirect redirect) {
        final HttpClient delegate = HttpClient.newBuilder()
                .followRedirects(redirect)
                .executor(this.pool)
                .build();
        this.delegates.add(delegate);
        return new ExtendedHttpClient(new ExtendedHttpClientConfiguration().setDelegate(delegate));
    }

    public void destroy(@OnEvent final Stop stop) {
        if (this.pool == null) {
            return;
        }
        this.redirectingClient.close();
        this.nonRedirectingClient.close();
        this.delegates.forEach(HttpClient::shutdownNow); // like the pool, do not wait for in-flight requests
        this.pool.shutdownNow();
        try {
            if (!this.pool.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warning("Async task not stopped in 2s (ES client)");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public <T> CompletionStage<T> request(final String method, final String url, final Object payload,
                                          final int timeout, final boolean enableRedirects, final Class<T> type,
                                          final String... headers) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        try {
            log.fine(String.format("Requesting '%s' on '%s' with timeout %s on type %s and headers %s", url, method, timeout, type.getName(), Arrays.toString(headers)));
            this.init();
            final HttpRequest request = this.toRequest(method, url, payload, timeout, headers);
            (enableRedirects ? this.redirectingClient : this.nonRedirectingClient)
                    .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            final Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                            log.severe("Could not process request properly :: " + cause.getMessage());
                            result.completeExceptionally(cause);
                        } else {
                            this.warnOnCrossHostRedirect(request.uri(), response);
                            this.onResponse(url, type, response, result);
                        }
                    });
        } catch (final Exception e) {
            log.severe("Could not submit request: " + e);
            result.completeExceptionally(e);
        }
        return result;
    }

    private HttpRequest toRequest(final String method, final String url, final Object payload,
                                  final int timeout, final String[] headers) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .method(method, payload == null ?
                        HttpRequest.BodyPublishers.noBody() :
                        HttpRequest.BodyPublishers.ofString(String.class.isInstance(payload) ?
                                String.valueOf(payload) : jsonMapper.toString(payload), StandardCharsets.UTF_8));
        if (timeout > 0) { // 0 means no timeout as with HttpURLConnection
            builder.timeout(Duration.ofMillis(timeout));
        }
        if (headers != null) {
            for (int i = 0; i + 1 < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
        }
        return builder.build();
    }

    // java.net.http drops Authorization on a redirect to another host but keeps the other headers (standard behavior)
    private void warnOnCrossHostRedirect(final URI requested, final HttpResponse<?> response) {
        if (response.previousResponse().isEmpty() || requested.getHost() == null) {
            return;
        }
        final String finalHost = response.uri().getHost();
        if (!requested.getHost().equalsIgnoreCase(finalHost) && warnedCrossHostRedirects.add(requested + " -> " + finalHost)) {
            log.warning(String.format(
                    "'%s' redirected to another host ('%s'), custom headers were sent to it (Authorization was not), " +
                            "use the final URL or disable followRedirects if it is not expected", requested, response.uri()));
        }
    }

    // the body is a stream so JSON is mapped without buffering the whole payload, it is only read as a String when needed
    private <T> void onResponse(final String url, final Class<T> resultType, final HttpResponse<InputStream> response,
                                final CompletableFuture<T> result) {
        try (final InputStream body = response.body()) { // always closed to release the connection
            final int responseCode = response.statusCode();
            if (Status.class == resultType) {
                log.fine("Complete result of type Status.class");
                result.complete(resultType.cast(new Status(responseCode, readString(body))));
                return;
            }
            if (responseCode >= 300 && responseCode < 400) { // only reached when redirects are not followed
                final String location = response.headers().firstValue("location").orElse("-");
                log.severe(String.format("HTTP response to '%s' was a HTTP %s redirecting to '%s' which is not followed", url, responseCode, location));
                result.completeExceptionally(new RedirectNotFollowedException(responseCode, location));
                return;
            }
            if (responseCode < 200 || responseCode >= 300) {
                log.severe(String.format("HTTP response to '%s' was a HTTP %s: '%s'", url, responseCode, readString(body)));
                result.completeExceptionally(new IllegalArgumentException("HTTP response was HTTP " + responseCode));
                return;
            }
            if (resultType == String.class) {
                result.complete(resultType.cast(readString(body)));
            } else {
                result.complete(jsonMapper.read(resultType, new InputStreamReader(body, StandardCharsets.UTF_8)));
            }
        } catch (final Exception e) {
            log.severe("Could not process request properly :: " + e.getMessage());
            result.completeExceptionally(e);
        }
    }

    private static String readString(final InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static class RedirectNotFollowedException extends IllegalArgumentException {
        private final int status;
        private final String location;

        public RedirectNotFollowedException(final int status, final String location) {
            super("HTTP response was HTTP " + status + " (redirect to '" + location + "' not followed)");
            this.status = status;
            this.location = location;
        }

        public int getStatus() {
            return status;
        }

        public String getLocation() {
            return location;
        }
    }
}
