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
