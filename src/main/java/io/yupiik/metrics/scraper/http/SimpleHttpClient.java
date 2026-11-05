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
import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.listener.impl.DefaultTimeout;
import io.yupiik.fusion.httpclient.core.listener.impl.ExchangeLogger;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.common.ThreadFactoryImpl;
import io.yupiik.metrics.scraper.configuration.HttpClientConfiguration;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.model.http.Status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private boolean closed;

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
        if (this.closed) {
            throw new IllegalStateException("HTTP client is closed");
        }
        if (this.pool != null) {
            return;
        }
        // java.net.http does the I/O in its selector thread (nio), this pool only completes the responses
        final int threads = this.configuration.threading() == null ? 4 : Math.max(1, this.configuration.threading().core());
        this.pool = Executors.newFixedThreadPool(threads, new ThreadFactoryImpl("metrics-scraper-http-client"));
        final List<RequestListener<?>> listeners = this.listeners();
        // redirect policy is set per client in java.net.http so keep one client per policy
        this.redirectingClient = this.newClient(HttpClient.Redirect.NORMAL, listeners);
        this.nonRedirectingClient = this.newClient(HttpClient.Redirect.NEVER, listeners);
    }

    private List<RequestListener<?>> listeners() {
        final HttpClientConfiguration http = this.configuration.http();
        final List<RequestListener<?>> listeners = new ArrayList<>(2);
        if (http == null || http.defaultTimeout() > 0) { // only applied to requests without timeout
            listeners.add(new DefaultTimeout(Duration.ofMillis(http == null ? 60_000L : http.defaultTimeout())));
        }
        if (http != null && http.logExchanges()) { // no payload (bulks can be big) nor headers (they can be secrets)
            listeners.add(new ExchangeLogger(
                    Logger.getLogger(SimpleHttpClient.class.getPackageName() + ".exchanges"), Clock.systemUTC(), false));
        }
        return listeners;
    }

    private ExtendedHttpClient newClient(final HttpClient.Redirect redirect, final List<RequestListener<?>> listeners) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(redirect)
                .executor(this.pool);
        final HttpClientConfiguration http = this.configuration.http();
        final long connectTimeout = http == null ? 30_000L : http.connectTimeout();
        if (connectTimeout > 0) {
            builder.connectTimeout(Duration.ofMillis(connectTimeout));
        }
        final HttpClient delegate = builder.build();
        this.delegates.add(delegate);
        return new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                .setDelegate(delegate)
                .setRequestListeners(listeners));
    }

    public synchronized void destroy(@OnEvent final Stop stop) {
        if (this.closed) {
            return;
        }
        this.closed = true;
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
            log.fine(() -> String.format("Requesting '%s' on '%s' with timeout %s on type %s and headers %s", url, method, timeout, type.getName(), Arrays.toString(headers)));
            this.init();
            final HttpRequest request = this.toRequest(method, url, payload, timeout, headers);
            // every result type needs the whole body (Status, String, JSON) and ofString() respects the response charset
            (enableRedirects ? this.redirectingClient : this.nonRedirectingClient)
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
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

    private <T> void onResponse(final String url, final Class<T> resultType, final HttpResponse<String> response,
                                final CompletableFuture<T> result) {
        try {
            final String body = response.body();
            final int responseCode = response.statusCode();
            if (Status.class == resultType) {
                log.fine("Complete result of type Status.class");
                result.complete(resultType.cast(new Status(responseCode, body)));
                return;
            }
            if (responseCode >= 300 && responseCode < 400) { // only reached when redirects are not followed
                final String location = response.headers().firstValue("location").orElse("-");
                log.severe(String.format("HTTP response to '%s' was a HTTP %s redirecting to '%s' which is not followed", url, responseCode, location));
                result.completeExceptionally(new RedirectNotFollowedException(responseCode, location));
                return;
            }
            if (responseCode < 200 || responseCode >= 300) {
                log.severe(String.format("HTTP response to '%s' was a HTTP %s: '%s'", url, responseCode, body));
                result.completeExceptionally(new IllegalArgumentException("HTTP response was HTTP " + responseCode));
                return;
            }
            if (resultType == String.class) {
                result.complete(resultType.cast(body));
            } else {
                result.complete(jsonMapper.fromString(resultType, body));
            }
        } catch (final Exception e) {
            log.severe("Could not process request properly :: " + e.getMessage());
            result.completeExceptionally(e);
        }
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
