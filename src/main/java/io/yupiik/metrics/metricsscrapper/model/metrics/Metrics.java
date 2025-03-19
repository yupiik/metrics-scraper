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
package io.yupiik.metrics.metricsscrapper.model.metrics;

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
