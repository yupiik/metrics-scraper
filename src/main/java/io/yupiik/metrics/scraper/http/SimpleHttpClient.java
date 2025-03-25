/*
 * Copyright (c) 2020-2022 - Yupiik SAS - https://www.yupiik.com - All right reserved
 *
 * This software and related documentation are provided under a license agreement containing restrictions on use and
 * disclosure and are protected by intellectual property laws. Except as expressly permitted in your license agreement
 * or allowed by law, you may not use, copy, reproduce, translate, broadcast, modify, license, transmit, distribute,
 * exhibit, perform, publish, or display any part, in any form, or by any means. Reverse engineering, disassembly, or
 * decompilation of this software, unless required by law for interoperability, is prohibited.
 *
 * The information contained herein is subject to change without notice and is not warranted to be error-free. If you
 * find any errors, please report them to us in writing.
 *
 * This software is developed for general use in a variety of information management applications. It is not developed
 * or intended for use in any inherently dangerous applications, including applications that may create a risk of personal
 * injury. If you use this software or hardware in dangerous applications, then you shall be responsible to take all
 * appropriate fail-safe, backup, redundancy, and other measures to ensure its safe use. Yupiik SAS and its affiliates
 * disclaim any liability for any damages caused by use of this software or hardware in dangerous applications.
 *
 * Yupiik and Meecrogate are registered trademarks of Yupiik SAS and/or its affiliates. Other names may be trademarks
 * of their respective owners.
 *
 * This software and documentation may provide access to or information about content, products, and services from third
 * parties. Yupiik SAS and its affiliates are not responsible for and expressly disclaim all warranties of any kind with
 * respect to third-party content, products, and services unless otherwise set forth in an applicable agreement between
 * you and Yupiik SAS. Yupiik SAS and its affiliates will not be responsible for any loss, costs, or damages incurred
 * due to your access to or use of third-party content, products, or services, except as set forth in an applicable
 * agreement between you and Yupiik SAS.
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
