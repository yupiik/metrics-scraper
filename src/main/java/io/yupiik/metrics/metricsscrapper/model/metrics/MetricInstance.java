package io.yupiik.metrics.metricsscrapper.model.metrics;

import java.util.Map;

public class MetricInstance {
    // shared
    private String help;
    private String name;
    private Map<String, String> tags;
    private OpenMetricMetricType type;
    private long timestamp;

    // counter, gauge, untyped
    private Double value;

    // histogram, summary
    private Double sum;
    private Double count;

    // summary
    private Map<String, Double> quantiles;

    // histogram
    private Map<String, Double> buckets;
    private Double min;
    private Double max;
    private Double mean;
    private Double stddev;

    public MetricInstance(String help, String name, Map<String, String> tags, OpenMetricMetricType toto,
                          long timestamp, Double value, Double sum, Double count, Map<String, Double> quantiles,
                          Map<String, Double> buckets, Double min, Double max, Double mean, Double stddev) {
        this.help = help;
        this.name = name;
        this.tags = tags;
        this.type = toto;
        this.timestamp = timestamp;
        this.value = value;
        this.sum = sum;
        this.count = count;
        this.quantiles = quantiles;
        this.buckets = buckets;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.stddev = stddev;
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

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
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

    public Double getSum() {
        return sum;
    }

    public void setSum(Double sum) {
        this.sum = sum;
    }

    public Double getCount() {
        return count;
    }

    public void setCount(Double count) {
        this.count = count;
    }

    public Map<String, Double> getQuantiles() {
        return quantiles;
    }

    public void setQuantiles(Map<String, Double> quantiles) {
        this.quantiles = quantiles;
    }

    public Map<String, Double> getBuckets() {
        return buckets;
    }

    public void setBuckets(Map<String, Double> buckets) {
        this.buckets = buckets;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getMean() {
        return mean;
    }

    public void setMean(Double mean) {
        this.mean = mean;
    }

    public Double getStddev() {
        return stddev;
    }

    public void setStddev(Double stddev) {
        this.stddev = stddev;
    }

    @Override
    public String toString() {
        return "MetricInstance{" +
                "help='" + help + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + tags +
                ", toto=" + type +
                ", timestamp=" + timestamp +
                ", value=" + value +
                ", sum=" + sum +
                ", count=" + count +
                ", quantiles=" + quantiles +
                ", buckets=" + buckets +
                ", min=" + min +
                ", max=" + max +
                ", mean=" + mean +
                ", stddev=" + stddev +
                '}';
    }
}
