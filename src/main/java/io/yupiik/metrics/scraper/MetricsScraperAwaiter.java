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
package io.yupiik.metrics.scraper;

import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.framework.build.api.order.Order;

import java.util.concurrent.CountDownLatch;

/**
 * Keeps the {@code Launcher} running until the container stops: without an awaiter,
 * fusion closes the container as soon as {@code main} returns.
 * With {@code -Dfusion.launcher.useHook=true} (set in the image), a {@code SIGTERM} closes the container
 * which fires {@link Stop} so the beans are properly destroyed.
 */
@ApplicationScoped
public class MetricsScraperAwaiter implements Awaiter {
    private final CountDownLatch stopped = new CountDownLatch(1);

    @Override
    public void await() {
        try {
            stopped.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void onStop(@OnEvent @Order(Integer.MAX_VALUE) final Stop stop) {
        stopped.countDown();
    }
}
