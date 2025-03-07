package io.yupiik.metrics.metricsscrapper.protocol;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.metrics.metricsscrapper.model.metrics.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class OpenMetricsReader {
    private final Logger log = Logger.getLogger(OpenMetricsReader.class.getName());
    private final Pattern linePattern = Pattern.compile("\r?\n");
    private final Pattern spacePattern = Pattern.compile("[ \t]+");

    public Metrics read(final String content, final long defaultTimestamp) {
        final String[] lines = linePattern.split(content);
        final Current current = new Current();
        final Metrics metrics = new Metrics();
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                final String type = line.substring(1).trim();
                if (type.startsWith("TYPE ")) {
                    final String[] typeSegments = spacePattern.split(type);
                    if (typeSegments.length != 3) {
                        log.warning(String.format("Invalid TYPE: '%s'", type));
                        continue;
                    }
                    final String metricName = typeSegments[1].trim();
                    if (current.getMetric() != null && !current.getMetric().equals(metricName)) {
                        current.setHelp(null);
                    }
                    current.setMetric(metricName);
                    current.setType(OpenMetricMetricType.valueOf(typeSegments[2].trim().toLowerCase(ROOT)));
                } else if (type.startsWith("HELP ")) {
                    final int sep = type.indexOf(' ', "HELP ".length() + 1);
                    current.setType(OpenMetricMetricType.untyped);
                    if (sep > 0) {
                        current.setHelp(type.substring(sep + 1).trim());
                        current.setMetric(type.substring("HELP ".length(), sep).trim());
                    } else {
                        current.setHelp(null);
                        current.setMetric(type.substring("HELP ".length()).trim());
                    }
                } // else comment, skip
                continue;
            }
            final MetricInstance metric = doParseMetric(defaultTimestamp, current, line);
            if (metric.getType() == OpenMetricMetricType.summary) {
                final String prefix = extractBaseName(metric.getName());
                Double sum = null;
                Double count = null;
                Map<String, Double> quantiles = new HashMap<>();
                if (metric.getName().startsWith(prefix + "_sum")) {
                    sum = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_count")) {
                    count = metric.getValue();
                } else if (metric.getName().startsWith(prefix)) {
                    if (metric.getTags().containsKey("quantile") && metric.getValue() != null) {
                        quantiles.put(metric.getTags().get("quantile"), metric.getValue());
                    }
                }
                for (int j = i + 1; j < lines.length; j++) {
                    final String subLine = lines[j];
                    if (subLine.startsWith(prefix + "_sum")) {
                        sum = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_count")) {
                        count = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix)) {
                        final MetricInstance quantile = doParseMetric(defaultTimestamp, current, subLine);
                        if (quantile.getTags().containsKey("quantile") && quantile.getValue() != null) {
                            quantiles.put(quantile.getTags().get("quantile"), quantile.getValue());
                        }
                        i++;
                    } else {
                        i = j - 1;
                        break;
                    }
                    i++;
                }
                metrics.getSummary().add(new MetricInstance(
                        metric.getHelp(), prefix,
                        metric.getTags().entrySet().stream()
                                .filter(it -> !"quantile".equals(it.getKey()))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new)),
                        metric.getType(), metric.getTimestamp(), null,
                        sum, count, quantiles, null, null, null, null, null));
            } else if (metric.getType() == OpenMetricMetricType.histogram) {
                final String prefix = extractBaseName(metric.getName());
                Double sum = null;
                Double count = null;
                Double min = null;
                Double max = null;
                Double mean = null;
                Double stddev = null;
                Map<String, Double> buckets = new HashMap<>();
                if (metric.getName().startsWith(prefix + "_bucket")) {
                    if (metric.getTags().containsKey("le") && metric.getValue() != null) {
                        buckets.put(metric.getTags().get("le"), metric.getValue());
                    }
                } else if (metric.getName().startsWith(prefix + "_sum")) {
                    sum = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_count")) {
                    count = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_min")) {
                    min = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_max")) {
                    max = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_mean")) {
                    mean = metric.getValue();
                } else if (metric.getName().startsWith(prefix + "_stddev")) {
                    stddev = metric.getValue();
                }
                for (int j = i + 1; j < lines.length; j++) {
                    final String subLine = lines[j];
                    if (subLine.startsWith(prefix + "_bucket")) {
                        final MetricInstance bucket = doParseMetric(defaultTimestamp, current, subLine);
                        if (bucket.getTags().containsKey("le") && bucket.getValue() != null) {
                            buckets.put(bucket.getTags().get("le"), bucket.getValue());
                        }
                        i++;
                    } else if (subLine.startsWith(prefix + "_sum")) {
                        sum = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_count")) {
                        count = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_min")) {
                        min = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_max")) {
                        max = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_mean")) {
                        mean = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else if (subLine.startsWith(prefix + "_stddev")) {
                        stddev = doParseMetric(defaultTimestamp, current, subLine).getValue();
                        i++;
                    } else {
                        i = j - 1;
                        break;
                    }
                }
                if (buckets.containsKey("+Inf")) {// supposed to be the same as count
                    final double posInf = buckets.remove("+Inf");
                    if (count == null) {
                        count = posInf;
                    }
                }
                metrics.getHistogram().add(new MetricInstance(
                        metric.getHelp(), prefix,
                        metric.getTags().entrySet().stream()
                                .filter(it -> !"le".equals(it.getKey()))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new)),
                        metric.getType(), metric.getTimestamp(), null,
                        sum, count, null, buckets, min, max, mean, stddev));
            } else { // single value
                switch (metric.getType()) {
                    case untyped:
                        metrics.getUntyped().add(metric);
                        break;
                    case counter:
                        metrics.getCounters().add(metric);
                        break;
                    case gauge:
                        metrics.getGauges().add(metric);
                        break;
                    default:
                        throw new IllegalArgumentException(current.getType().name());
                }
            }
        }
        return metrics;
    }

    private String extractBaseName(final String name) {
        if (name.endsWith("_bucket")) {
            return name.substring(0, name.length() - "_bucket".length());
        }
        if (name.endsWith("_min")) {
            return name.substring(0, name.length() - "_min".length());
        }
        if (name.endsWith("_max")) {
            return name.substring(0, name.length() - "_max".length());
        }
        if (name.endsWith("_mean")) {
            return name.substring(0, name.length() - "_mean".length());
        }
        if (name.endsWith("_stddev")) {
            return name.substring(0, name.length() - "_stddev".length());
        }
        if (name.endsWith("_sum")) {
            return name.substring(0, name.length() - "_sum".length());
        }
        if (name.endsWith("_count")) {
            return name.substring(0, name.length() - "_count".length());
        }
        if (name.endsWith("_current")) {
            return name.substring(0, name.length() - "_current".length());
        }
        if (name.endsWith("_total")) {
            return name.substring(0, name.length() - "_total".length());
        }
        if (name.endsWith("_elapsedTime")) {
            return name.substring(0, name.length() - "_elapsedTime".length());
        }
        if (name.endsWith("_minTimeDuration")) {
            return name.substring(0, name.length() - "_minTimeDuration".length());
        }
        if (name.endsWith("_maxTimeDuration")) {
            return name.substring(0, name.length() - "_maxTimeDuration".length());
        }
        if (name.endsWith("_rate_per_second")) {
            return name.substring(0, name.length() - "_rate_per_second".length());
        }
        if (name.endsWith("_one_min_rate_per_second")) {
            return name.substring(0, name.length() - "_one_min_rate_per_second".length());
        }
        if (name.endsWith("_five_min_rate_per_second")) {
            return name.substring(0, name.length() - "_five_min_rate_per_second".length());
        }
        if (name.endsWith("_fifteen_min_rate_per_second")) {
            return name.substring(0, name.length() - "_fifteen_min_rate_per_second".length());
        }
        return name;
    }

    private MetricInstance doParseMetric(final long defaultTimestamp, final Current current, final String line) {
        // <name>{tag1="v 1",tag2="400"}    <value> [<timestamp>]
        final int endTags = line.indexOf('}');
        final String[] segments = endTags > 0 ?
                Stream.concat(
                                Stream.of(line.substring(0, endTags + 1).trim()),
                                Stream.of(spacePattern.split(line.substring(endTags + 1).trim())))
                        .toArray(String[]::new) :
                spacePattern.split(line);
        if (segments.length < 2 || segments.length > 3) {
            throw new IllegalArgumentException("Invalid line: '" + line + "'");
        }
        final MetricWithTags metricWithTags = parseMetricWithTags(segments[0]);
        if (current.getMetric() != null && !metricWithTags.getMetric().startsWith(current.getMetric())) {
            current.setHelp(null);
            current.setType(OpenMetricMetricType.untyped);
            current.setMetric(metricWithTags.getMetric());
        }

        return new MetricInstance(
                current.getHelp(), metricWithTags.getMetric(), metricWithTags.getTags(), current.getType(),
                segments.length == 2 ? defaultTimestamp : goParseInt(segments[segments.length - 1]),
                goParseFloat(segments[segments.length - (segments.length == 2 ? 1 : 2)]),
                null, null, null, null, null, null, null, null);
    }

    private long goParseInt(final String segment) {
        return Long.parseLong(segment);
    }

    private double goParseFloat(final String segment) {
        return switch (segment) {
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "Nan" -> Double.NaN;
            default -> Double.parseDouble(segment);
        };
    }

    private MetricWithTags parseMetricWithTags(final String segment) {
        final int tagDelimiter = segment.indexOf('{');
        if (tagDelimiter < 0) {
            return new MetricWithTags(segment, emptyMap());
        }
        if (!segment.endsWith("}")) {
            throw new IllegalArgumentException("Missing tag for metric name with tag end character: '" + segment + "'");
        }
        return new MetricWithTags(
                segment.substring(0, tagDelimiter),
                split(segment.substring(tagDelimiter + 1, segment.length() - 1), ',')
                        .map(String::trim)
                        .filter(it -> it.contains("="))
                        .collect(toMap(it -> it.substring(0, it.indexOf('=')), it -> {
                            final String value = it.substring(it.indexOf('=') + 1);
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                return value.substring(1, value.length() - 1);
                            }
                            return value;
                        }, (a, b) -> a, TreeMap::new)));
    }

    private Stream<String> split(final String string, final char sep) {
        final Collection<String> output = new ArrayList<>();
        final StringBuilder builder = new StringBuilder(string.length());
        boolean escaped = false;
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char current = chars[i];
            if (current == '\\' && i < chars.length - 1 && (chars[i + 1] == '\n' || chars[i + 1] == '\\' || chars[i + 1] == '"')) {
                if (!escaped) {
                    escaped = true;
                } else {
                    builder.append(current);
                    escaped = false;
                }
            } else if (current == sep) {
                if (!builder.isEmpty()) {
                    output.add(builder.toString());
                    builder.setLength(0);
                }
            } else {
                builder.append(current);
            }
        }
        if (!builder.isEmpty()) {
            output.add(builder.toString());
        }
        return output.stream();
    }

}
