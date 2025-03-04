package io.yupiik.metrics.metricsscrapper.elasticsearch;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.metrics.metricsscrapper.configuration.ElasticsearchClientConfiguration;
import io.yupiik.metrics.metricsscrapper.http.SimpleHttpClient;
import io.yupiik.metrics.metricsscrapper.model.domain.*;
import io.yupiik.metrics.metricsscrapper.model.elasticsearch.DocumentResult;
import io.yupiik.metrics.metricsscrapper.model.elasticsearch.index.*;
import io.yupiik.metrics.metricsscrapper.model.elasticsearch.request.BulkRequest;
import io.yupiik.metrics.metricsscrapper.model.elasticsearch.response.BulkResponse;
import io.yupiik.metrics.metricsscrapper.model.elasticsearch.response.BulkResponseItem;
import io.yupiik.metrics.metricsscrapper.model.http.Status;
import io.yupiik.metrics.metricsscrapper.model.metrics.Metrics;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private final ElasticsearchClientConfiguration config;
    private final JsonMapper jsonMapper;

    private final SimpleHttpClient httpClient;

    private ZoneId zone;
    private Function<Class<?>, String> currentIndex;

    private final boolean useWildcardForOperations;
    private final String base;
    private final String query;
    private final String[] headers;
    private final int timeout;

    private final CompletableFuture<?> initialized;

    private final Map<Class<?>, String> indicesNames = new HashMap<>();

    public ElasticsearchClient(final ElasticsearchClientConfiguration config, final JsonMapper jsonMapper, final SimpleHttpClient httpClient) {
        this.httpClient = httpClient;
        this.config = config;
        this.jsonMapper = jsonMapper;

        this.base = config.base();
        this.query = config.refreshOnWrite() ? "?refresh" : "";

        final Map<String, String> headers = config.headers();
        this.headers = Stream.concat(
                        Stream.concat(
                                !headers.containsKey("Content-Type") ? Stream.of("Content-Type", "application/json") : Stream.empty(),
                                !headers.containsKey("Accept") ? Stream.of("Accept", "application/json") : Stream.empty()),
                        headers.entrySet().stream()
                                .flatMap(it -> Stream.of(it.getKey(), it.getValue())))
                .toArray(String[]::new);
        this.timeout = (int) Math.max(0, config.timeout());

        this.indicesNames.putAll(getIndexableClasses().collect(toMap(identity(), this::toIndex)));

        final boolean isDynamicIndexName = config.indexNameSuffix() != null;
        this.useWildcardForOperations = isDynamicIndexName; // <name>-<date>

        if (isDynamicIndexName) {
            this.initIndexFactory(config.indexNameSuffix());
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
                                    log.log(Level.FINE, "Index template '{0}' already exists", templateName);
                                    return completedFuture(true); // done
                                });
                    })
                    .toArray(CompletableFuture<?>[]::new);
            this.initialized = this.toInitialized(config, tasks);
        } else {
            final CompletableFuture<?>[] tasks = this.getIndexableClasses()
                    .map(this::ensureMapping)
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            currentIndex = this.getIndicesNames()::get;
            this.initialized = this.toInitialized(config, tasks);
        }
    }

    protected <T> CompletionStage<T> request(String mtd, String url, Object payload, Class<T> returnedType,
                                             int timeout, boolean redirected, String[] headers) {
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
        //TODO eventually handle mappings deeper (not sure)
        final IndexTemplate template = new IndexTemplate(List.of(templateName + "*"), new Template(new ElasticsearchIndexSettingsIndex(config.settingsTemplate().index().shards(), config.settingsTemplate().index().replicas()), this.createMappings(type)));
        final String tpl = jsonMapper.toString(template);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Creating index template '{0}':\n{1}", new Object[]{templateName, tpl});
        } else {
            log.log(Level.INFO, "Creating index template '{0}'", templateName);
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
        final Mappings mappings = new Mappings(List.of());
        for (final Field declaredField : type.getDeclaredFields()) {
            mappings.properties().add(new Property(declaredField.getName(), this.getEsType(declaredField.getType())));
        }
        return mappings;
    }

    private String getEsType(final Type model) {
        //TODO handle complex toto eventually, and implement a finer mapping
        if (boolean.class == model || Boolean.class == model) {
            return "boolean";
        } else if (String.class == model) {
            return "text";
        } else if (double.class == model || float.class == model || Double.class == model || Float.class == model) {
            return "double";
        } else if (int.class == model || short.class == model || byte.class == model || long.class == model || Integer.class == model || Short.class == model || Byte.class == model || Long.class == model) {
            return "long";
        } else if (this.isStringable(model)) {
            return "text";
        } else {
            //TODO handle complex toto eventually
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
                                        "GET", base + '/' + getAllIndices() + "/_refresh", null,
                                        Status.class, timeout, true, headers)
                                .thenApply(s -> {
                                    log.log(Level.FINE, "Refresh after mapping creation: {0}", s);
                                    return null;
                                }));
            }
            return allOf(tasks).thenApply(it -> {
                log.log(Level.FINE, "Initialized mapping without refresh");
                return it;
            }).exceptionally(error -> {
                log.log(Level.SEVERE, error.getMessage(), error);
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

    public <T> CompletionStage<DocumentResult> delete(final String documentId, final Class<T> instanceType) { // todo: ignore 404?
        return this.initialized.thenCompose(i -> this.request("DELETE", base + '/' + findQueryIndexationIndex(instanceType) + "/" + documentId + query,
                null, DocumentResult.class, timeout, true, headers));
    }

    public CompletionStage<?> reset() {
        return this.initialized
                .thenCompose(i -> refresh(null)) // ensure written indices are available to delete
                .thenCompose(i -> this.request("POST", base + '/' + getAllIndices() + "/_delete_by_query" + query, "{\"query\":{\"match_all\":{}}}",
                        DocumentResult.class, timeout, true, headers))
                .thenAccept(i -> log.info("Deleted documents"))
                .thenCompose(i -> this.refresh(null));
    }

    public CompletionStage<BulkResponse> bulk(final List<BulkRequest> lines) {
        return this.initialized.thenCompose(i -> this.request("POST", base + "/_bulk" + query,
                lines.stream().map(this::toBulkLine).collect(joining("\n")) + '\n', BulkResponse.class, timeout, true, headers));
    }

    private String toBulkLine(BulkRequest bulkRequest) {
        return "{ \"" + bulkRequest.actionType().getCode() + "\" : { \"_index\" : \"" + bulkRequest._index() +
                "\", \"_id\" : \"" + bulkRequest._id() + "\" } }\n" + (bulkRequest.actionType().hasDocument() ? jsonMapper.toString(bulkRequest.document()) : "");
    }

    public String toIndex(final Class<?> type) {
        return this.toIndexName(type.getSimpleName());
    }

    public CompletionStage<Void> createIndex(final String index) {
        log.log(Level.INFO, "Creating elasticsearch index '{0}'", index);
        return this.request("PUT", base + '/' + index,
                        "{\"settings\":" + jsonMapper.toString(config.settingsTemplate().index()) + "}",
                        Status.class, timeout, true, headers)
                .thenApply(status -> ensure200(index, status, "Can't create properly"));
    }

    protected <T> String findCurrentIndexationIndex(final Class<T> type) {
        return requireNonNull(currentIndex.apply(type), "No index for " + type) + "/_doc";
    }

    protected <T> String findQueryIndexationIndex(final Class<T> instance) {
        return requireNonNull(getIndicesNames().get(instance), "No index for " + instance) + "*/_doc";
    }

    private String getAllIndices() {
        return useWildcardForOperations ?
                indicesNames.values().stream().map(it -> it + '*').collect(joining(",")) :
                String.join(",", indicesNames.values());
    }

    protected <T> String findIndexOrFail(final Class<T> instance, final boolean query) {
        final String index = indicesNames.get(instance);
        if (index == null) {
            throw new IllegalArgumentException(instance + " not indexable");
        }
        return index + (query && useWildcardForOperations ? "*" : "") + "/_doc";
    }

    private CompletionStage<Void> ensureMapping(final Class<?> type) {
        final String index = this.toIndex(type);
        return this.hasSchema(index).thenCompose(has -> {
            if (!has) {
                return this.createIndex(index).thenCompose(ignored -> this.createMapping(index, this.createMappings(type)));
            }
            log.log(Level.FINE, "Index '{0}' already exists", index);
            return completedFuture(null);
        });
    }

    private CompletionStage<Void> createMapping(final String index, final Mappings mapping) {
        log.log(Level.INFO, "Creating elasticsearch mapping for index '{0}'", index);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "> ES mapping: {0}", mapping);
        }
        return this.request("PUT", base + '/' + index + "/_mapping", mapping, Status.class,
                        timeout, true, headers)
                .thenApply(status -> ensure200(index, status, "Can't create properly mapping for"));
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
        final StringBuilder builder = new StringBuilder(config.indexPrefix());
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
        return this.bulk(this.toBulk(metrics, customTags)).thenApply(result -> {
            if (!result.errors()) {
                return result; // ok
            }
            final Map<String, Object> items = result.items();
            if (items == null) {
                log.log(Level.SEVERE, "Index update failed: {0}", result);
                return result;
            }
            final List<String> errors = items.values().stream()
                    .filter(o -> BulkResponseItem.class.isInstance(o) && BulkResponseItem.class.cast(o).error() != null)
                    .map(o -> BulkResponseItem.class.cast(o).error().reason())
                    .collect(toList());
            if (!errors.isEmpty()) {
                log.log(Level.SEVERE, "Error during bulk metric push:\n{0}", String.join("\n", errors));
            }
            return errors;
        }).toCompletableFuture();
    }

    public List<BulkRequest> toBulk(final Metrics metrics, final Map<String, String> customTags) {
        return Stream.of(
                        metrics.getCounters().stream()
                                .map(it -> new Counter(
                                        it.getHelp(), it.getName(), merge(it.getTags(), customTags), it.getToto(), it.getTimestamp(), it.getValue())),
                        metrics.getGauges().stream()
                                .map(it -> new Gauge(
                                        it.getHelp(), it.getName(), merge(it.getTags(), customTags), it.getToto(), it.getTimestamp(), it.getValue())),
                        metrics.getUntyped().stream()
                                .map(it -> new Untyped(
                                        it.getHelp(), it.getName(), merge(it.getTags(), customTags), it.getToto(), it.getTimestamp(), it.getValue())),
                        metrics.getHistogram().stream()
                                .map(it -> new Histogram(
                                        it.getHelp(), it.getName(), merge(it.getTags(), customTags), it.getToto(),
                                        it.getTimestamp(), it.getSum(), it.getCount(), it.getBuckets(),
                                        it.getMin(), it.getMax(), it.getMean(), it.getStddev())),
                        metrics.getSummary().stream()
                                .map(it -> new Summary(
                                        it.getHelp(), it.getName(), merge(it.getTags(), customTags), it.getToto(),
                                        it.getTimestamp(), it.getSum(), it.getCount(), it.getQuantiles())))
                .flatMap(s -> s.flatMap(this::toBulkRequests))
                .collect(toList());
    }

    private Map<String, String> merge(final Map<String, String> tags, final Map<String, String> customTags) {
        if (customTags == null || customTags.isEmpty()) {
            return tags;
        }
        if (tags == null || tags.isEmpty()) {
            return customTags;
        }
        return Stream.of(tags, customTags) // tags win over custom tags (specific over global)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    private Stream<Class<?>> getModels() {
        return Stream.of(Counter.class, Gauge.class, Untyped.class, Histogram.class, Summary.class);
    }

    private Map<Class<?>, String> getStaticIndexNames(final Function<Class<?>, String> toIndex) {
        return getModels().collect(toMap(identity(), toIndex));
    }

    private void initIndexFactory(final String pattern) {
        final Map<Class<?>, String> indexBases = this.getStaticIndexNames(this::toIndex);

        // when computing the name once a day avoid to recompute and check indices all the time
        final ConcurrentMap<Class<?>, DailyIndexName> runtimeIndices = new ConcurrentHashMap<>();
        try { // use constant names as aliases
            final DateTimeFormatter formatter = DateTimeFormatter.class.cast(DateTimeFormatter.class.getField(pattern).get(null));
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

    private Stream<BulkRequest> toBulkRequests(final Object metric) {
        return Stream.of(new BulkRequest(currentIndex.apply(metric.getClass()), null, metric, BulkRequest.BulkActionType.index));
    }

}
