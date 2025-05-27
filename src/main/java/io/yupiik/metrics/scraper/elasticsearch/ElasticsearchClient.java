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
package io.yupiik.metrics.scraper.elasticsearch;

import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.scraper.configuration.ElasticsearchClientConfiguration;
import io.yupiik.metrics.scraper.configuration.MetricsScraperConfiguration;
import io.yupiik.metrics.scraper.http.SimpleHttpClient;
import io.yupiik.metrics.scraper.model.domain.*;
import io.yupiik.metrics.scraper.model.elasticsearch.DocumentResult;
import io.yupiik.metrics.scraper.model.elasticsearch.index.*;
import io.yupiik.metrics.scraper.model.elasticsearch.request.BulkRequest;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponse;
import io.yupiik.metrics.scraper.model.elasticsearch.response.BulkResponseItem;
import io.yupiik.metrics.scraper.model.http.Status;
import io.yupiik.metrics.scraper.model.metrics.Metrics;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

@ApplicationScoped
public class ElasticsearchClient {

    private final Logger log = Logger.getLogger(ElasticsearchClient.class.getName());

    private final MetricsScraperConfiguration configuration;
    private final JsonMapper jsonMapper;
    private final SimpleHttpClient httpClient;

    private ZoneId zone;
    private Function<Class<?>, String> currentIndex;

    private boolean useWildcardForOperations;
    private String base;
    private String query;
    private String[] headers;
    private int timeout;

    private CompletableFuture<?> initialized;

    private final Map<Class<?>, String> indicesNames = new HashMap<>();

    public ElasticsearchClient(final MetricsScraperConfiguration configuration, final JsonMapper jsonMapper, final SimpleHttpClient httpClient) {
        this.configuration = configuration;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    public void start(@OnEvent Start start) {
        log.info("> Initializing ElasticsearchClient");
        log.fine("> Configuration: " + configuration);
        this.base = configuration.elasticsearch().base();
        this.query = configuration.elasticsearch().refreshOnWrite() ? "?refresh" : "";

        final Map<String, String> headers = ofNullable(configuration.elasticsearch().headers()).orElseGet(HashMap::new);
        this.headers = Stream.concat(
                        Stream.concat(
                                !headers.containsKey("Content-Type") ? Stream.of("Content-Type", "application/json") : Stream.empty(),
                                !headers.containsKey("Accept") ? Stream.of("Accept", "application/json") : Stream.empty()),
                        headers.entrySet().stream()
                                .flatMap(it -> Stream.of(it.getKey(), it.getValue())))
                .toArray(String[]::new);
        this.timeout = (int) Math.max(0, configuration.elasticsearch().timeout());

        this.indicesNames.putAll(getIndexableClasses().collect(toMap(identity(), this::toIndex)));
        log.fine(String.format("Generating index names %s", String.join(", ", indicesNames.values())));

        final boolean isDynamicIndexName = configuration.elasticsearch().indexNameSuffix() != null;
        this.useWildcardForOperations = isDynamicIndexName; // <name>-<date>

        if (isDynamicIndexName) {
            log.fine("> Dynamic index name - Using wildcards for operations");
            this.initIndexFactory(configuration.elasticsearch().indexNameSuffix());
            zone = ZoneId.of("UTC");
            final CompletableFuture<?>[] tasks = getIndexableClasses()
                    .map(type -> {
                        final String templateName = this.toIndex(type).replaceAll("\\*", "");
                        return this.request(
                                        "GET", base + "/_index_template/" + templateName,
                                        null, Status.class, timeout, true, this.headers)
                                .thenCompose(exists -> {
                                    if (exists.value() == 404) { // then create it
                                        return this.createIndexTemplate(type, templateName).toCompletableFuture();
                                    }
                                    log.fine(String.format("Index template '%s' already exists", templateName));
                                    return completedFuture(true); // done
                                });
                    })
                    .toArray(CompletableFuture<?>[]::new);
            this.initialized = this.toInitialized(configuration.elasticsearch(), tasks);
        } else {
            log.fine("> Static index name");
            final CompletableFuture<?>[] tasks = this.getIndexableClasses()
                    .map(this::ensureMapping)
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            currentIndex = this.getIndicesNames()::get;
            this.initialized = this.toInitialized(configuration.elasticsearch(), tasks);
        }
    }

    protected <T> CompletionStage<T> request(String mtd, String url, Object payload, Class<T> returnedType,
                                             int timeout, boolean redirected, String[] headers) {
        log.fine("Request method: " + mtd);
        log.fine("Request url: " + url);
        log.fine("Request payload: " + payload);
        log.fine("Request returnType: " + (returnedType != null ? returnedType.toString() : "null"));
        log.fine("Request timeout: " + timeout);
        log.fine("Request redirected: " + redirected);
        log.fine("Request headers: " + String.join(", ", headers));

        if (Status.class == returnedType) {
            return httpClient.request(mtd, url, payload, timeout, redirected, Status.class, headers)
                    .thenApply(s -> returnedType.cast(new Status(s.value(), s.payload())));
        }
        return httpClient.request(mtd, url, payload, timeout, redirected, returnedType, headers);
    }

    protected Stream<Class<?>> getIndexableClasses() {
        return Stream.of(Counter.class, Gauge.class, Untyped.class, Histogram.class, Summary.class);
    }

    private CompletionStage<Boolean> createIndexTemplate(final Class<?> type, final String templateName) {
        log.fine("Create index template.");
        final IndexTemplate template = new IndexTemplate(List.of(templateName + "*"), new Template(new ElasticsearchIndexSettingsIndex(configuration.elasticsearch().settingsTemplate().index().shards(), configuration.elasticsearch().settingsTemplate().index().replicas()), this.createMappings(type)));
        final String tpl = jsonMapper.toString(template);
        if (log.isLoggable(Level.FINE)) {
            log.fine(String.format("Creating index template '%s':\n%s", templateName, tpl));
        } else {
            log.info(String.format("Creating index template '%s'", templateName));
        }
        return this.request(
                        "PUT", base + "/_index_template/" + templateName,
                        tpl, Status.class, timeout, true, headers)
                .thenApply(status -> {
                    if (status.value() > 399) {
                        throw new IllegalStateException("Invalid index creation: " + status);
                    }
                    return true;
                });
    }

    private Mappings createMappings(final Class<?> type) {
        log.fine("Create mappings for type: " + type.getSimpleName());
        final Mappings mappings = new Mappings(new HashMap<>());
        for (final Field declaredField : type.getDeclaredFields()) {
            log.fine("Create property in mappings for field: " + declaredField.getName());
            mappings.properties().put(declaredField.getName(), new Property(this.getEsType(declaredField.getType())));
        }
        for (final Field declaredField : type.getSuperclass().getDeclaredFields()) {
            log.fine("Create property in mappings for field: " + declaredField.getName());
            mappings.properties().put(declaredField.getName(), new Property(this.getEsType(declaredField.getType())));
        }
        return mappings;
    }

    private String getEsType(final Type model) {
        log.fine("Compute ES type for property type: " + model.getTypeName());
        if (boolean.class == model || Boolean.class == model) {
            return "boolean";
        } else if (String.class == model) {
            return "text";
        } else if (double.class == model || float.class == model || Double.class == model || Float.class == model) {
            return "double";
        } else if (int.class == model || short.class == model || byte.class == model || long.class == model
                || Integer.class == model || Short.class == model || Byte.class == model || Long.class == model) {
            return "long";
        } else if (Map.class == model || List.class == model || Set.class == model || Collection.class == model
                || Object[].class == model || KeyValue.class == model) {
            return "object";
        } else if (this.isStringable(model)) {
            return "text";
        } else {
            return "text";
        }
    }

    private boolean isStringable(final Type model) {
        return Date.class == model || model.getTypeName().startsWith("java.time.") || Class.class == model || Type.class == model;
    }

    private CompletableFuture<?> toInitialized(final ElasticsearchClientConfiguration config, final CompletableFuture<?>[] tasks) {
        if (tasks.length > 0) {
            if (config.refreshOnWrite()) {
                return allOf(tasks)
                        .thenCompose(i -> this.request(
                                        "GET", base + '/' + this.getAllIndices() + "/_refresh", null,
                                        Status.class, timeout, true, headers)
                                .thenApply(s -> {
                                    log.fine(String.format("Refresh after mapping creation: %s", s));
                                    return null;
                                }));
            }
            return allOf(tasks).thenApply(it -> {
                log.fine("Initialized mapping without refresh");
                return it;
            }).exceptionally(error -> {
                log.log(Level.SEVERE, "Could not initialize mapping due to following error: " + error.getMessage(), error);
                if (RuntimeException.class.isInstance(error)) {
                    throw RuntimeException.class.cast(error);
                }
                throw new IllegalStateException(error);
            });
        }
        return completedFuture(true);
    }

    public <T> CompletionStage<Void> refresh(final Class<T> type) {
        final String index = type == null ? null : ofNullable(indicesNames.get(type)).orElseThrow(() -> new IllegalArgumentException("No index for '" + type + "'"));
        return this.initialized.thenCompose(i -> this.request("GET", base + '/' +
                                (index == null ? getAllIndices() : index) + "/_refresh" + query,
                        null, Status.class, timeout, true, headers))
                .thenApply(status -> {
                    if (status.value() != 201 && status.value() != 200) {
                        throw new IllegalArgumentException("'" + index + "' refresh failed, got " + status);
                    }
                    return null;
                });
    }

    public <T> CompletionStage<DocumentResult> index(final String documentId, final T instance) {
        return this.initialized.thenCompose(i -> this.request("PUT", base + '/' + findCurrentIndexationIndex(instance.getClass()) + "/" + documentId + query,
                instance, DocumentResult.class, timeout, true, headers));
    }

    public CompletionStage<BulkResponse> bulk(final List<BulkRequest> lines) {
        final long maxPayloadSizeBytes = this.configuration.elasticsearch().maxBulkRequestSize();
        final List<CompletionStage<BulkResponse>> futures = new ArrayList<>();

        final List<String> currentBatch = new ArrayList<>();
        int currentSize = 0;

        for (final BulkRequest request : lines) {
            final String bulkLine = this.toBulkLine(request);
            final int bulkLineSize = bulkLine.getBytes(StandardCharsets.UTF_8).length;

            if (currentSize + bulkLineSize > maxPayloadSizeBytes && !currentBatch.isEmpty()) {
                futures.add(this.sendBulkBatch(currentBatch));
                currentBatch.clear();
                currentSize = 0;
            }

            currentBatch.add(bulkLine);
            currentSize += bulkLineSize;
        }

        if (!currentBatch.isEmpty()) {
            futures.add(this.sendBulkBatch(currentBatch));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                final List<BulkResponse> responses = futures.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());

                return this.mergeBulkResponses(responses);
            });
    }

    private BulkResponse mergeBulkResponses(final List<BulkResponse> responses) {
        int took = 0;
        boolean errors = false;
        final Map<String, Object> items = new HashMap<>();
        for (final BulkResponse response : responses) {
            took += response.took();
            if (response.errors()) {
                errors = true;
            }
            if(response.items() != null){
                items.putAll(response.items());
            }
        }
        return new BulkResponse(took, errors, items);
    }

    private CompletionStage<BulkResponse> sendBulkBatch(final List<String> batchLines) {
        final String payload = String.join("\n", batchLines) + '\n';
        return this.initialized.thenCompose(i ->
            this.request("POST", base + "/_bulk" + query, payload, BulkResponse.class, timeout, true, headers)
        );
    }

    private String toBulkLine(final BulkRequest bulkRequest) {
        final StringBuilder sb = new StringBuilder("{ \"");
        sb.append(bulkRequest.getActionType().getCode())
                .append("\" : { \"_index\" : \"")
                .append(bulkRequest.getIndex())
                .append("\"");
        if (bulkRequest.getId() != null) {
            sb.append(", \"_id\" : \"")
                    .append(bulkRequest.getId())
                    .append("\"");
        }
        sb.append("} }\n").append(bulkRequest.getActionType().hasDocument() ? bulkRequest.getDocument().json() : "");
        log.finer("Built bulk line : " + sb);
        return sb.toString();
    }

    public String toIndex(final Class<?> type) {
        return this.toIndexName(type.getSimpleName());
    }

    public CompletionStage<Void> createIndex(final String index) {
        log.info(String.format("Creating elasticsearch index '%s'", index));
        return this.request("PUT", base + '/' + index,
                        "{\"settings\":" + jsonMapper.toString(configuration.elasticsearch().settingsTemplate().index()) + "}",
                        Status.class, timeout, true, headers)
                .thenApply(status -> ensure200(index, status, "Can't create properly"));
    }

    protected <T> String findCurrentIndexationIndex(final Class<T> type) {
        return requireNonNull(currentIndex.apply(type), "No index for " + type) + "/_doc";
    }

    private String getAllIndices() {
        return useWildcardForOperations ?
                indicesNames.values().stream().map(it -> it + '*').collect(joining(",")) :
                String.join(",", indicesNames.values());
    }

    private CompletionStage<Void> ensureMapping(final Class<?> type) {
        final String index = this.toIndex(type);
        return this.hasSchema(index).thenCompose(has -> {
            if (!has) {
                return this.createIndex(index).thenCompose(ignored -> this.createMapping(index, this.createMappings(type)));
            }
            log.fine(String.format("Index '%s' already exists", index));
            return completedFuture(null);
        });
    }

    private CompletionStage<Void> createMapping(final String index, final Mappings mapping) {
        log.info(String.format("Creating elasticsearch mapping for index '%s'", index));
        if (log.isLoggable(Level.FINE)) {
            log.fine(String.format("> ES mapping: %s", mapping));
        }
        return this.request("PUT", base + '/' + index + "/_mapping", mapping, Status.class,
                        timeout, true, headers)
                .thenApply(status -> this.ensure200(index, status, "Can't create properly mapping for"));
    }

    private Void ensure200(final String index, final Status status, final String msg) {
        if (status.value() != 200) {
            throw new IllegalArgumentException(msg + " index '" + index + "', got " + status);
        }
        return null;
    }

    private CompletionStage<Boolean> hasSchema(final String index) {
        return this.request("HEAD", base + '/' + index, null, Status.class, timeout, true, headers)
                .thenApply(status -> status.value() == 200);
    }

    protected String toIndexName(final String name) {
        final StringBuilder builder = new StringBuilder(configuration.elasticsearch().indexPrefix());
        for (final char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (builder.charAt(builder.length() - 1) != '-') {
                    builder.append('-');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString()
                .replace("-a-p-i-", "-api-"); // known particular case
    }

    public Map<Class<?>, String> getIndicesNames() {
        return this.indicesNames;
    }

    public CompletableFuture<?> createBulk(final Metrics metrics, final Map<String, String> customTags, final long timestamp) {
        return doBulk(metrics, customTags).toCompletableFuture();
    }

    private CompletionStage<?> doBulk(final Metrics metrics, final Map<String, String> customTags) {
        log.fine("Creating elasticsearch bulk");
        return this.bulk(this.toBulk(metrics, customTags)).thenApply(result -> {
            if (!result.errors()) {
                return result; // ok
            }
            final Map<String, Object> items = result.items();
            if (items == null) {
                log.severe(String.format("Index update failed: %s", result));
                return result;
            }
            final List<String> errors = items.values().stream()
                    .filter(o -> BulkResponseItem.class.isInstance(o) && BulkResponseItem.class.cast(o).error() != null)
                    .map(o -> BulkResponseItem.class.cast(o).error().reason())
                    .collect(toList());
            if (!errors.isEmpty()) {
                log.severe(String.format("Error during bulk metric push:\n%s", String.join("\n", errors)));
            }
            return errors;
        }).toCompletableFuture();
    }

    public List<BulkRequest> toBulk(final Metrics metrics, final Map<String, String> customTags) {
        return Stream.of(
                        metrics.getCounters().stream()
                                .map(it -> new Counter(
                                        it.getMetricName(), new KeyValue(it.getName(), it.getValue()),
                                        it.getLabels(), customTags, it.getType(), this.toTimeStamp(it.getTimestamp()))),
                        metrics.getGauges().stream()
                                .map(it -> new Gauge(
                                        it.getMetricName(), new KeyValue(it.getName(), it.getValue()),
                                        it.getLabels(), customTags, it.getType(), this.toTimeStamp(it.getTimestamp()))),
                        metrics.getUntyped().stream()
                                .map(it -> new Untyped(
                                        it.getMetricName(), new KeyValue(it.getName(), it.getValue()),
                                        it.getLabels(), customTags, it.getType(), this.toTimeStamp(it.getTimestamp()))),
                        metrics.getHistogram().stream()
                                .map(it -> new Histogram(
                                        it.getMetricName(), new KeyValue(it.getName(), it.getValue()),
                                        it.getLabels(), customTags, it.getType(), this.toTimeStamp(it.getTimestamp()))),
                        metrics.getSummary().stream()
                                .map(it -> new Summary(
                                        it.getMetricName(), new KeyValue(it.getName(), it.getValue()),
                                        it.getLabels(), customTags, it.getType(), this.toTimeStamp(it.getTimestamp()))))
                .flatMap(s -> s.flatMap(this::toBulkRequests))
                .collect(toList());
    }

    private String toTimeStamp(long timestamp) {
        final Instant instant = Instant.ofEpochMilli(timestamp);
        final LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.of(configuration.timezone()));
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        return localDateTime.format(formatter);
    }

    private Stream<Class<?>> getModels() {
        return Stream.of(Counter.class, Gauge.class, Untyped.class, Histogram.class, Summary.class);
    }

    private Map<Class<?>, String> getStaticIndexNames(final Function<Class<?>, String> toIndex) {
        return getModels().collect(toMap(identity(), toIndex));
    }

    private void initIndexFactory(final String pattern) {
        log.fine("Initializing index factory with pattern: " + pattern);
        final Map<Class<?>, String> indexBases = this.getStaticIndexNames(this::toIndex);

        // when computing the name once a day avoid to recompute and check indices all the time
        final ConcurrentMap<Class<?>, DailyIndexName> runtimeIndices = new ConcurrentHashMap<>();
        try { // use constant names as aliases
            final DateTimeFormatter formatter = DateTimeFormatter.class.cast(DateTimeFormatter.class.getField(pattern).get(null));
            log.log(Level.FINER, String.format("Found date formatter: %s", formatter.toString()));
            if (pattern.contains("_TIME")) { // refresh once a sec
                log.log(Level.FINE, "Will create one index per second, this is likely too often, you may want to adjust your suffix pattern");
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusSeconds(1).withNano(0).toInstant());
            } else { // refresh once a day
                log.log(Level.FINE, "Will create one index per day");
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant());
            }
        } catch (final IllegalAccessException | NoSuchFieldException e) {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (pattern.contains("s")) { // sec
                log.log(Level.FINE, "Will create one index per second, this is likely too often, you may want to adjust your suffix pattern");
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusSeconds(1).withNano(0).toInstant());
            } else if (pattern.contains("m")) { // minute
                log.log(Level.FINE, "Will create one index per minute, this is likely too often, you may want to adjust your suffix pattern");
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusMinutes(1).withSecond(0).withNano(0).toInstant());
            } else if (pattern.contains("H")) { // hour
                log.log(Level.FINE, "Will create one index per hour"); // no warning, it is "ok-ish"
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant());
            } else { // let's default on once a day (good for prod)
                log.log(Level.FINE, "Will create one index per day");
                currentIndex = type -> computeIndex(
                        indexBases, formatter, runtimeIndices, type,
                        now -> toOffsetDateTime(now).plusSeconds(1).withNano(0).toInstant());
            }
        } finally {
            log.log(Level.FINE, () -> "Index factory initialized. Current index: " + currentIndex.toString());
        }
    }

    private String computeIndex(final Map<Class<?>, String> indices, final DateTimeFormatter formatter,
                                final ConcurrentMap<Class<?>, DailyIndexName> runtimeIndices, final Class<?> type,
                                final Function<Instant, Instant> invalidDateComputer) {
        DailyIndexName current = runtimeIndices.get(type);
        final Instant now = Instant.now();
        if (current == null || current.getInvalidAt().isBefore(now)) {
            current = new DailyIndexName(
                    invalidDateComputer.apply(now),
                    indices.get(type) + '-' + formatter.format(ZonedDateTime.ofInstant(now, zone).toOffsetDateTime()),
                    "");
            runtimeIndices.put(type, current);
            return current.getValue();
        }
        return current.getValue();
    }

    private OffsetDateTime toOffsetDateTime(final Instant now) {
        return ZonedDateTime.ofInstant(now, zone).toOffsetDateTime();
    }

    private Stream<BulkRequest> toBulkRequests(final OpenMetric metric) {
        return Stream.of(new BulkRequest(currentIndex.apply(metric.getClass()), null, metric, BulkRequest.BulkActionType.index));
    }

}
