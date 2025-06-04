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
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.model.statistics.StatisticMetric;
import io.yupiik.metrics.scraper.model.statistics.Statistics;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class StatisticsReader {
    private final Logger log = Logger.getLogger(StatisticsReader.class.getName());

    private final static int MAX_OBJECTS = 1;
    private final static String NODES = "nodes";
    private final static List<String> FILTERS = List.of("plugins", "network_types", "mappings");

    private JsonMapper jsonMapper;

    public StatisticsReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Statistics read(final String content, final long defaultTimestamp) {
        AtomicBoolean hop = new AtomicBoolean(false);
        final var statistics = new Statistics();
        final var currentStatistic = jsonMapper.read(CurrentStatistic.class, new StringReader(content));
        final var statisticMetrics = currentStatistic.extensions().entrySet()
                .stream().map(entry -> {
                    if(entry.getValue() instanceof Map<?,?>) {
                        // Particular case of /_nodes/{nodeId}/stats, we skip the (thank you ES) {nodeID} provided in the response
                        //TODO improvement needed, condition size == 1 is really poor ..
                        if(Objects.equals(NODES, entry.getKey()) && ((Map<?, ?>) entry.getValue()).size() == 1) {
                            final Map<String, Object> nodesMap = (LinkedHashMap<String, Object>) entry.getValue();
                            if(nodesMap.entrySet().iterator().next().getValue() instanceof Map<?,?>) {
                                final Map<String, Object> nodesIdMap = (LinkedHashMap<String, Object>) nodesMap.entrySet().iterator().next().getValue();
                                return nodesIdMap.entrySet().stream().map(nodeIdEntry  -> {
                                    if(nodeIdEntry.getValue() instanceof Map<?,?>) {
                                        hop.set(true);
                                        return this.splitAndFilter((Map<String, Object>) nodeIdEntry.getValue(), MAX_OBJECTS)
                                                .stream().map(map -> new StatisticMetric(defaultTimestamp, nodeIdEntry.getKey(), map))
                                                .toList();
                                    } else {
                                        return List.of(new StatisticMetric(defaultTimestamp, nodeIdEntry.getKey(), nodeIdEntry.getValue()));
                                    }
                                }).flatMap(List::stream)
                                        .toList();
                            } else {
                                return List.of(new StatisticMetric(defaultTimestamp, entry.getKey(), entry.getValue()));
                            }
                        } else {
                            return this.splitAndFilter((Map<String, Object>) entry.getValue(), MAX_OBJECTS)
                                    .stream().map(map -> new StatisticMetric(defaultTimestamp, entry.getKey(), map))
                                    .toList();
                        }
                    } else {
                        return List.of(new StatisticMetric(defaultTimestamp, entry.getKey(), entry.getValue()));
                    }
                })
                .flatMap(List::stream)
                .filter(statisticMetric ->
                        statisticMetric.getValue() != null
                                || !(statisticMetric.getValue() instanceof String && (Objects.equals(statisticMetric.getValue(), "") || Objects.equals(statisticMetric.getValue(), "{}")))
                                || !(statisticMetric.getValue() instanceof Map<?,?> && ((Map<?, ?>) statisticMetric.getValue()).isEmpty())
                ).toList();
        statistics.getStatisticMetrics().addAll(statisticMetrics);
        log.fine(() -> "Read statistics: " + statistics);
        return statistics;
    }

    private List<Map<String, Object>> splitAndFilter(final Map<String, Object> map, final int splitSize)
    {
        final List<Map<String, Object>> splits = new ArrayList<>();
        int size = map.size();
        int processed = 0;
        while(processed < size){
            int currentSplitSize = Math.min((size - processed), splitSize);
            splits.add(map.entrySet().stream()
                    .skip(processed)
                    .limit(currentSplitSize)
                    .peek(entry -> {
                        if(entry.getValue() instanceof Map<?,?>) {
                            FILTERS.forEach(filter -> ((LinkedHashMap<String, Object>) entry.getValue()).remove(filter));
                        }
                    })
                    .filter(entry -> !FILTERS.contains(entry.getKey()) && !(entry.getValue() instanceof Map<?,?> && ((Map) entry.getValue()).isEmpty()) )
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            processed = processed + currentSplitSize;
        }
        return splits;
    }

    @JsonModel
    public record CurrentStatistic(
            @JsonOthers Map<String, Object> extensions
    ){
        @Override
        public String toString() {
            return "CurrentStatistic{" +
                    "extensions=" + extensions +
                    '}';
        }
    }
}
