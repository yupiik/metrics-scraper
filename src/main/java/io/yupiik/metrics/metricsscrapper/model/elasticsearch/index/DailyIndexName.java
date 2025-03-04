package io.yupiik.metrics.metricsscrapper.model.elasticsearch.index;

import java.time.Instant;

public class DailyIndexName {
    private Instant invalidAt;
    private String value;
    private String template;

    public DailyIndexName(Instant invalidAt, String value, String template) {
        this.invalidAt = invalidAt;
        this.value = value;
        this.template = template;
    }

    public Instant getInvalidAt() {
        return invalidAt;
    }

    public void setInvalidAt(Instant invalidAt) {
        this.invalidAt = invalidAt;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
