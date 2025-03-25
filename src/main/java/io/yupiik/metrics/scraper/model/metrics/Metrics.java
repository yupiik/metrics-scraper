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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class Metrics {
    private List<MetricInstance> counters = new ArrayList<>();
    private List<MetricInstance> gauges = new ArrayList<>();
    private List<MetricInstance> untyped = new ArrayList<>();
    private List<MetricInstance> histogram = new ArrayList<>();
    private List<MetricInstance> summary = new ArrayList<>();

    public List<MetricInstance> getCounters() {
        return counters;
    }

    public void setCounters(List<MetricInstance> counters) {
        this.counters = counters;
    }

    public List<MetricInstance> getGauges() {
        return gauges;
    }

    public void setGauges(List<MetricInstance> gauges) {
        this.gauges = gauges;
    }

    public List<MetricInstance> getUntyped() {
        return untyped;
    }

    public void setUntyped(List<MetricInstance> untyped) {
        this.untyped = untyped;
    }

    public List<MetricInstance> getHistogram() {
        return histogram;
    }

    public void setHistogram(List<MetricInstance> histogram) {
        this.histogram = histogram;
    }

    public List<MetricInstance> getSummary() {
        return summary;
    }

    public void setSummary(List<MetricInstance> summary) {
        this.summary = summary;
    }

    public boolean isEmpty() {
        return Stream.of(counters, gauges, untyped, histogram, summary).allMatch(Collection::isEmpty);
    }

    public void dropZeroCounters() {
        counters.removeIf(it -> it.getValue() == null || it.getValue() == 0);
    }

    @Override
    public String toString() {
        return "Metrics{" +
                "\nCounters(" + counters.size() + "):\n    " + counters +
                ", \nGauges(" + gauges.size() + "):\n    " + gauges +
                ", \nUntyped(" + untyped.size() + "):\n    " + untyped +
                ", \nHistogram(" + histogram.size() + "):\n    " + histogram +
                ", \nSummary(" + summary.size() + "):\n    " + summary +
                "\n}";
    }
}
