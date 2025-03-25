package io.yupiik.metrics.scraper.model.metrics;

import java.util.Map;

public class MetricWithTags {
    private String metric;
    private Map<String, String> tags;

    public MetricWithTags(String metric, Map<String, String> tags) {
        this.metric = metric;
        this.tags = tags;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
