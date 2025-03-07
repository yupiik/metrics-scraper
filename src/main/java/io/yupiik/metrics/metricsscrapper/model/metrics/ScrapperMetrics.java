package io.yupiik.metrics.metricsscrapper.model.metrics;

import io.yupiik.metrics.metricsscrapper.configuration.Scrapper;

public class ScrapperMetrics {
    private long timestamp;
    private Scrapper configuration;
    private Metrics metrics;

    public ScrapperMetrics(long timestamp, Scrapper configuration, Metrics metrics) {
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

    public Scrapper getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Scrapper configuration) {
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
        return "ScrapperMetrics{" +
                "timestamp=" + timestamp +
                ", configuration=" + configuration +
                ", metrics=" + metrics +
                '}';
    }
}
