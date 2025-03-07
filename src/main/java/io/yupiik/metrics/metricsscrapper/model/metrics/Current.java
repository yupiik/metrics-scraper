package io.yupiik.metrics.metricsscrapper.model.metrics;

public class Current {
    protected String metric;
    protected String help;
    protected OpenMetricMetricType type;

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public OpenMetricMetricType getType() {
        return type;
    }

    public void setType(OpenMetricMetricType type) {
        this.type = type;
    }
}
