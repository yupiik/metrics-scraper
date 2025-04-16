/*
 * Copyright (c) 2025 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.metrics.scraper.protocol;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.metrics.scraper.model.metrics.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
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
            final MetricInstance metric = this.doParseMetric(defaultTimestamp, current, line);
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
                case summary:
                    metrics.getSummary().add(metric);
                    break;
                case histogram:
                    metrics.getHistogram().add(metric);
                    break;
                default:
                    throw new IllegalArgumentException(current.getType().name());
            }
        }
        return metrics;
    }

    private MetricInstance doParseMetric(final long defaultTimestamp, final Current current, final String line) {
        // <name>{tag1="v 1",tag2="400"}    <value> [<timestamp>]
        final int endTags = line.lastIndexOf('}');
        final String[] segments = endTags > 0 ?
                Stream.concat(
                                Stream.of(line.substring(0, endTags + 1).trim()),
                                Stream.of(spacePattern.split(line.substring(endTags + 1).trim())))
                        .toArray(String[]::new) :
                spacePattern.split(line);
        if (segments.length < 2 || segments.length > 3) {
            throw new IllegalArgumentException("Invalid line: '" + line + "'");
        }
        final MetricWithTags metricWithTags = this.parseMetricWithTags(segments[0]);
        if (current.getMetric() != null && !metricWithTags.getMetric().startsWith(current.getMetric())) {
            current.setHelp(null);
            current.setType(OpenMetricMetricType.untyped);
            current.setMetric(metricWithTags.getMetric());
        }

        return new MetricInstance(current.getMetric(),
                current.getHelp(), metricWithTags.getMetric(), metricWithTags.getTags(), current.getType(),
                segments.length == 2 ? defaultTimestamp : this.goParseInt(segments[segments.length - 1]),
                this.goParseFloat(segments[segments.length - (segments.length == 2 ? 1 : 2)]));
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
                this.split(segment.substring(tagDelimiter + 1, segment.length() - 1), ',')
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
