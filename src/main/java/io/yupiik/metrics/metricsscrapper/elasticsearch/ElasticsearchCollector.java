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
package io.yupiik.metrics.metricsscrapper.elasticsearch;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.metrics.metricsscrapper.model.metrics.Metrics;
import io.yupiik.metrics.metricsscrapper.model.metrics.ScrapperMetrics;

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

    public void onMetrics(@OnEvent final ScrapperMetrics scrapperMetrics) {
        log.fine("> onMetrics");
        final Metrics metrics = scrapperMetrics.getMetrics();
        if (closing.get() || metrics.isEmpty()) {
            log.fine("> empty metrics or closed collector");
            return;
        }
        final CompletableFuture<?> promise = esClient.createBulk(metrics, scrapperMetrics.getConfiguration().tags(), scrapperMetrics.getTimestamp());
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
