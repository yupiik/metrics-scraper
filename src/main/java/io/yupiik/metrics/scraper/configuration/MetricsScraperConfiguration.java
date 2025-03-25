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
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.util.List;

// hosts the application configuration, you can add @Param as you need
// by default fusion makes it injectable directly in any bean
@RootConfiguration("metrics-scraper") // will be the prefix of the system properties filtered to bind on the instance
public record MetricsScraperConfiguration(
        @Property(value = "defaultScraping", documentation = "Global Scraping - Default when not set in a scraper.")
        ScrapingConfiguration defaultScraping,

        @Property(value="scrapers", documentation = "Scrapers - Where to scrap data from.")
        List<Scraper> scrapers,

        @Property(value = "threading", documentation = "Threading - Threading used by the asynchronous HTTP client.")
        ScraperHttpClientThreading threading,

        @Property(value = "elasticsearch", documentation = "Elasticsearch - Elasticsearch configuration to send metrics to Elasticsearch.")
        ElasticsearchClientConfiguration elasticsearch,

        @Property(value = "timezone", documentation = "Define the timezone to be used for timestamps interpretation.", defaultValue = "\"UTC\"")
        String timezone
) {
}
