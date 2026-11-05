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
package io.yupiik.metrics.scraper.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

public record ScraperHttpClientThreading(
        @Property(value = "core", documentation = "Threads - How many threads complete HTTP asynchronous requests (I/O is non-blocking so a few threads are enough).", defaultValue = "4")
        int core,

        @Property(value = "max", documentation = "Max Threads - Ignored since the HTTP client uses non-blocking I/O, kept for configuration compatibility (use `core`).", defaultValue = "4")
        int max
) {
}
