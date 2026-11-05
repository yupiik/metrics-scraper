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

public record HttpClientConfiguration(
        @Property(value = "connectTimeout", documentation = "Connect Timeout - How long establishing a connection can last (ms), `0` to use the JVM default.", defaultValue = "30000L")
        long connectTimeout,

        @Property(value = "defaultTimeout", documentation = "Default Timeout - Request timeout (ms) used when a request does not set one (`timeout=0`), `0` to disable it.", defaultValue = "60000L")
        long defaultTimeout,

        @Property(value = "logExchanges", documentation = "Log Exchanges - Log each HTTP exchange (method, URL, duration and response status) at `INFO` level with the `io.yupiik.metrics.scraper.http.exchanges` logger. Headers and payloads are not logged.", defaultValue = "false")
        boolean logExchanges
) {
}
