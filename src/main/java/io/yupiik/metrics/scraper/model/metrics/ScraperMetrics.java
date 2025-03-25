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
package io.yupiik.metrics.scraper.model.metrics;

import io.yupiik.metrics.scraper.configuration.Scraper;

public class ScraperMetrics {
    private long timestamp;
    private Scraper configuration;
    private Metrics metrics;

    public ScraperMetrics(long timestamp, Scraper configuration, Metrics metrics) {
        this.timestamp = timestamp;
        this.configuration = configuration;
        this.metrics = metrics;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Scraper getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Scraper configuration) {
        this.configuration = configuration;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        return "ScraperMetrics{" +
                "timestamp=" + timestamp +
                ", configuration=" + configuration +
                ", metrics=" + metrics +
                '}';
    }
}
