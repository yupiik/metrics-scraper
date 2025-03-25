package io.yupiik.metrics.scraper.model.metrics;

import java.util.Map;

public class MetricInstance {
    // shared
    private String help;
    private String name;
    private String metricName;
    private Map<String, String> labels;
    private OpenMetricMetricType type;
    private long timestamp;
    private Double value;


    public MetricInstance(final String metricName, final String help, final String name, final Map<String, String> labels, final OpenMetricMetricType type,
                          final long timestamp, final Double value) {
        this.metricName = metricName;
        this.help = help;
        this.name = name;
        this.labels = labels;
        this.type = type;
        this.timestamp = timestamp;
        this.value = value;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public OpenMetricMetricType getType() {
        return type;
    }

    public void setType(OpenMetricMetricType type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\nMetricInstance{" +
                "metric name='" + metricName + '\'' +
                ", help='" + help + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + labels +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", value=" + value +
                "}";
    }
}
