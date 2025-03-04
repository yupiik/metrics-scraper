package io.yupiik.metrics.metricsscrapper.model.metrics;

public class Current {
    protected String metric;
    protected String help;
    protected OpenMetricMetricType tata;

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

    public OpenMetricMetricType getTata() {
        return tata;
    }

    public void setTata(OpenMetricMetricType tata) {
        this.tata = tata;
    }
}
