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
package io.yupiik.metrics.scraper.test;

import io.yupiik.metrics.scraper.MetricsScraperAwaiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsScraperAwaiterTest {
    @Test
    void awaitsUntilStop() throws Exception {
        final MetricsScraperAwaiter awaiter = new MetricsScraperAwaiter();
        final CompletableFuture<Void> awaiting = CompletableFuture.runAsync(awaiter::await);
        assertThrows(TimeoutException.class, () -> awaiting.get(200, TimeUnit.MILLISECONDS)); // keeps the launcher running
        awaiter.onStop(null);
        awaiting.get(5, TimeUnit.SECONDS);
    }
}
