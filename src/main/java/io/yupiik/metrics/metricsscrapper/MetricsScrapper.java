package io.yupiik.metrics.metricsscrapper;

import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.metrics.metricsscrapper.configuration.MetricsScrapperConfiguration;
import io.yupiik.metrics.metricsscrapper.configuration.Scrapper;
import io.yupiik.metrics.metricsscrapper.configuration.ScrappingConfiguration;
import io.yupiik.metrics.metricsscrapper.elasticsearch.ElasticsearchCollector;
import io.yupiik.metrics.metricsscrapper.http.SimpleHttpClient;
import io.yupiik.metrics.metricsscrapper.model.http.Status;
import io.yupiik.metrics.metricsscrapper.model.metrics.Metrics;
import io.yupiik.metrics.metricsscrapper.model.metrics.ScrapperMetrics;
import io.yupiik.metrics.metricsscrapper.protocol.OpenMetricsReader;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;

@ApplicationScoped
public class MetricsScrapper {

    private static final Logger log = Logger.getLogger(MetricsScrapper.class.getName());

    private final MetricsScrapperConfiguration configuration;
    private final OpenMetricsReader openMetricsReader;
    private final SimpleHttpClient httpClient;
    private final ElasticsearchCollector esCollector;
    private final Emitter emitter;
    private ScheduledExecutorService executor;

    public MetricsScrapper(final MetricsScrapperConfiguration configuration, final OpenMetricsReader openMetricsReader, final SimpleHttpClient httpClient,
                           final ElasticsearchCollector esCollector, final Emitter emitter) {
        this.configuration = configuration;
        this.openMetricsReader = openMetricsReader;
        this.httpClient = httpClient;
        this.esCollector = esCollector;
        this.emitter = emitter;
    }

    public void start(@OnEvent Start start){
        log.info("> Initializing MetricsScrapper");
        log.info("> Configuration: " + configuration);
        this.executor = Executors.newScheduledThreadPool(10); //TODO add threading configuration with defaults
        final ScrappingConfiguration defaultScrapping = configuration.defaultScrapping();
        if (configuration.scrappers() == null || configuration.scrappers().isEmpty()) {
            log.info("No scrappers defined, exiting");
            return;
        }

        final Map<String, String> configHeader = ofNullable(configuration.elasticsearch().headers()).orElseGet(HashMap::new);
        final String[] headers = Stream.concat(
                        Stream.concat(
                                !configHeader.containsKey("Content-Type") ? Stream.of("Content-Type", "application/json") : Stream.empty(),
                                !configHeader.containsKey("Accept") ? Stream.of("Accept", "application/json") : Stream.empty()),
                        configHeader.entrySet().stream()
                                .flatMap(it -> Stream.of(it.getKey(), it.getValue())))
                .toArray(String[]::new);
        final int timeout = (int) Math.max(0, configuration.elasticsearch().timeout());
        final String base = configuration.elasticsearch().base();
        this.httpClient.request("GET", base, null, timeout, true, Status.class, headers)
                .handle((r, error) -> {
                    log.fine(String.format("Got response from ES '%s'", r));
                    return r;
                });
        configuration.scrappers().forEach(scrapper -> this.launchScrapping(scrapper, defaultScrapping, httpClient, openMetricsReader, esCollector, emitter));
    }

    private void launchScrapping(final Scrapper scrapper, final ScrappingConfiguration defaultScrapping, final SimpleHttpClient http,
                                 final OpenMetricsReader openMetricsReader, final ElasticsearchCollector esCollector, final Emitter emitter) {
        if (scrapper.url() == null) {
            log.info(String.format("Scrapper %s has no url, skipping", scrapper));
            return;
        }
        final ScrappingConfiguration scrapping = ofNullable(scrapper.scrapping()).orElse(defaultScrapping);
        if (scrapping.interval() <= 0) {
            log.info(String.format("Skipping scrapper %s since interval is <= 0 (%s)", scrapper, scrapping.interval()));
            return;
        }
        log.info(String.format("Scheduling scrapping for %s", scrapper));
        final ScrapperRuntime runtime = new ScrapperRuntime(http, scrapper, openMetricsReader, esCollector, emitter);
        //scheduler.schedule(new ScheduledTask.Default(scrapping.interval(), runtime));
        this.executor.scheduleAtFixedRate(runtime, 0, scrapping.interval(), TimeUnit.MILLISECONDS);
    }

    private static class ScrapperRuntime implements Runnable {
        private final SimpleHttpClient http;
        private final OpenMetricsReader openMetricsReader;
        private final ElasticsearchCollector esCollector;
        private final Scrapper config;

        //private final Function<Function<String, String>, String> urlFactory;
        private final int timeout;
        private final String[] headers;
        private final AtomicBoolean calling = new AtomicBoolean();
        //private volatile Discovered discovered;
        private final Emitter emitter;

        private ScrapperRuntime(final SimpleHttpClient http, final Scrapper config, final OpenMetricsReader openMetricsReader, final ElasticsearchCollector esCollector, final Emitter emitter) {
            this.http = http;
            this.config = config;
            this.openMetricsReader = openMetricsReader;
            this.esCollector = esCollector;

            this.emitter = emitter;
            this.timeout = (int) Math.max(0, config.timeout());
            this.headers = config.headers() == null ? new String[0] : config.headers().entrySet().stream()
                    .flatMap(it -> Stream.of(it.getKey(), it.getValue()))
                    .toArray(String[]::new);

//            final KubernetesDiscovery discovery = config.kubernetesDiscovery();
//            if (discovery != null) {
//                if (discovery.selector() == null) {
//                    log.log(Level.WARNING, "No kubernetes selector set in {0}", config);
//                }
//                //TODO implement kubernetes client management
////                this.urlFactory = substitutor.compile(
////                        ofNullable(discovery.urlTemplate())
////                                .orElseGet(() -> ofNullable(config.url())
////                                        .orElse("http://${authority}/meecrogate/api/metrics/prometheus?diffId=" + config.hashcode())));
//            } else {
//                this.urlFactory = null;
//            }
        }

        @Override
        public void run() {
            if (!calling.compareAndSet(false, true)) {
                log.fine(String.format("Skipping scrapping of '%s'", config));
                return;
            }
            //TODO implement k8s management
            if(this.usesKubernetes()) {
//                final Discovered currentDiscovered = discovered;
//                if ((currentDiscovered == null || currentDiscovered.shouldRefresh(clock))) {
//                    final Discovered newDiscovered = new Discovered(
//                            findK8sAuthorities().stream()
//                                    .map(it -> urlFactory.apply(k -> {
//                                        switch (k) {
//                                            case "authority":
//                                                return it;
//                                            case "configHashCode":
//                                                return Integer.toString(config.hashCode());
//                                            default:
//                                                throw new IllegalArgumentException("unknown template '" + k + "'");
//                                        }
//                                    }))
//                                    .collect(toList()),
//                            clock.millis() + Math.max(config.kubernetesDiscovery().getDiscoveryValidity(), 0));
//                    discovered = newDiscovered;
//                    newDiscovered.urls.forEach(this::doScrapping);
//                } else { // use cached discovery
//                    currentDiscovered.urls.forEach(this::doScrapping);
//                }
            } else {
                this.doScrapping(config.url());
            }
        }

//        private List<String> findK8sAuthorities() { // todo: enable to inject the client to inherit from a reactive client?
//            final KubernetesDiscovery discovery = config.kubernetesDiscovery();
//            return kubernetesClient.findAuthoritiesByLabel(discovery.selector(), discovery.namespace());
//        }

        private void doScrapping(final String url) {
            log.fine(String.format("Scrapping '%s' on '%s'", config, url));
            try {
                http.request("GET", url, null, timeout, true, Status.class, headers).handle((r, error) -> {
                    try {
                        log.fine(String.format("Got scrapping of '%s'", r));
                        if (error != null) {
                            log.log(Level.SEVERE, error.getMessage(), error);
                        } else if (r.value() != config.expectedResponseCode()) {
                            if (log.isLoggable(Level.FINE)) {
                                log.warning(String.format("Expected status %s but got %s (%s)", config.expectedResponseCode(), r.value(), r.payload()));
                            } else {
                                log.warning(String.format("Expected status %s but got %s", config.expectedResponseCode(), r.value()));
                            }
                        } else if (!r.payload().trim().isEmpty()) {
                            log.fine(String.format("Open metrics read: %s", r.payload()));
                            this.onResponse(r.payload());
                        } else {
                            log.fine(String.format("Got an empty payload from %s", url));
                        }
                    } finally {
                        calling.set(false);
                    }
                    return r;
                });
            } catch (final RuntimeException re) { // don't break scheduler for that
                log.log(Level.SEVERE, re.getMessage(), re);
                calling.set(false);
            }
        }

        private void onResponse(final String payload) {
            final long timestamp = Instant.now().toEpochMilli();
            final Metrics metrics = openMetricsReader.read(payload, timestamp);
            if (config.skipZero()) {
                metrics.dropZeroCounters();
            }
            if (!metrics.isEmpty()) {
                this.emitter.emit(new ScrapperMetrics(timestamp, config, metrics));
            } else {
                log.fine(String.format("No metrics found in %s", payload));
            }
        }

        private boolean usesKubernetes() {
            return (config.url() == null || config.url().trim().isEmpty()) && config.kubernetesDiscovery() != null;
        }
    }

//    private static class Discovered {
//        private final Collection<String> urls;
//        private final long validUntil;
//
//        private Discovered(Collection<String> urls, long validUntil) {
//            this.urls = urls;
//            this.validUntil = validUntil;
//        }
//
//        private boolean shouldRefresh(final Clock clock) {
//            return validUntil < clock.millis();
//        }
//    }

    public void onStop(@OnEvent final Stop stop) {
        log.info("> Stop: " + stop);
        this.executor.shutdownNow();
    }
}
