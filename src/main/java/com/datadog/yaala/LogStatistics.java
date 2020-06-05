package com.datadog.yaala;

import com.datadog.yaala.util.MeanCounter;
import com.datadog.yaala.util.StepConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import static io.micrometer.core.instrument.Metrics.globalRegistry;
import static java.lang.Double.compare;
import static java.lang.Math.max;
import static java.util.Comparator.reverseOrder;
import static java.util.Optional.empty;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

/**
 * This class is responsible to ingesting {@link LogEvent}s and accumulating all interesting statistics
 * regarding the latter, as well as handling all alerting logic.
 *
 * @author Nicolas Estrada.
 */
@SuppressWarnings({"DuplicateStringLiteralInspection", "MagicCharacter"})
public class LogStatistics {

    private final Config cfg;
    private final MeterRegistry reg;
    private final MeanCounter totalRequests;
    private final Map<String, RouteStats> routeStats;
    private final SortedSet<RouteStats> topHits;

    // alerting logic
    private long tentativeAlertTriggerTime;
    private long tentativeAlertRemovalTime;
    private long alertTriggerTime;
    private double alertRps;

    LogStatistics(Config cfg) {
        this.cfg = cfg;
        this.reg = new SimpleMeterRegistry(new StepConfig(cfg.step()), cfg.clock());
        this.totalRequests = newAveragingCounter(HTTP_REQUESTS);
        this.routeStats = new HashMap<>(16);
        this.topHits = new TreeSet<>(reverseOrder());
        globalRegistry.add(reg);
    }

    /**
     * Updates the underlying metrics upon ingestion of the arg <i>evt</i>.
     */
    void ingest(LogEvent evt) {
        totalRequests.increment();
        var prefix = routeSection(evt.getRoute(), cfg.routeDepth());
        var stats = routeStats.computeIfAbsent(prefix, k -> new RouteStats(prefix));
        // In order to maintain the sorted invariant of top hits, since the object is mutable,
        // it is necessary to remove it then add it again
        topHits.remove(stats);
        stats.hits.increment();
        stats.bytesSent.increment(evt.getBytesSent());
        topHits.add(stats);
    }

    /**
     * Same as above but just increments the total requests counter.
     * The log line couldn't be parsed but it <i>is</i> a hit so don't forget it.
     */
    void incRequests() {
        totalRequests.increment();
    }

    /*
     * Alerting state machine follows.
     */

    /**
     * Refreshes the current alert status.
     */
    public void refreshAlert() {
        boolean newTrafficSpike = tentativeAlertTriggerTime == 0;
        boolean alertIsTriggering = alertTriggerTime > 0;
        if (totalRps() >= cfg.alertThreshold()) {
            if (newTrafficSpike) {
                markTentativeAlert();
            } else if (!alertIsTriggering && shouldTriggerAlert()) {
                triggerAlert();
            }
        } else if (alertIsTriggering && shouldRemoveAlert()) {
            resetAlert();
        }
    }

    private void markTentativeAlert() {
        assert alertTriggerTime == 0 && tentativeAlertRemovalTime == 0;
        this.tentativeAlertTriggerTime = cfg.clock().wallTime();
    }

    private void triggerAlert() {
        alertTriggerTime = tentativeAlertTriggerTime;
        alertRps = totalRps();
        tentativeAlertRemovalTime = cfg.clock().wallTime() + cfg.alertCooldown().toMillis();
    }

    private boolean shouldTriggerAlert() {
        return tentativeAlertTriggerTime + cfg.alertDelay().toMillis() <= cfg.clock().wallTime();
    }

    private boolean shouldRemoveAlert() {
        return cfg.clock().wallTime() >= tentativeAlertRemovalTime;
    }

    private void resetAlert() {
        tentativeAlertTriggerTime = 0;
        tentativeAlertRemovalTime = 0;
        alertTriggerTime = 0;
        alertRps = 0;
    }

    /**
     * @return the alert trigger time if an alert is being fired, {@link Optional#empty()} otherwise.
     */
    public Optional<Entry<Long, Double>> getAlertTriggerTime() {
        return alertTriggerTime > 0 ?
          Optional.of(new SimpleImmutableEntry<>(alertTriggerTime, alertRps)) :
          empty();
    }

    // End of alerting logic

    /**
     * @return a snapshot of the route statistics for displaying purposes.
     */
    public Iterable<Map<String, Object>> getRouteStatistics() {
        return topHits
          .stream()
          .map(RouteStats::toMap)
          .collect(toList());
    }

    /**
     * @return the total number of requests per second.
     */
    public double totalRps() {
        return totalRequests.mean(SECONDS);
    }

    /**
     * Should return the "section" of a route given a "maximum depth".
     * <p>
     * For a depth of 1, a section is defined as being what's before the second '/' in the resource section of the line.
     * For example, the section for "/pages/create" is "/pages"
     * </p>
     */
    static String routeSection(String route, int maxDepth) {
        int cutoffIx = 0;
        for (int i = 0; i < maxDepth; i++) {
            int ix = route.indexOf('/', cutoffIx + 1);
            if (ix > 0) {
                cutoffIx = ix;
            } else {
                cutoffIx = route.length();
                break;
            }
        }
        String section = route.substring(0, max(1, cutoffIx));
        // Cleanup query params
        int qIx = section.indexOf('?');
        if (qIx > 0) {
            section = section.substring(0, qIx);
        }
        return section;
    }

    /**
     * Tracks a monotonically increasing value and a rolling mean/avg.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new {@link MeanCounter}.
     */
    private MeanCounter newAveragingCounter(String name, String... tags) {
        return new MeanCounter(reg.counter(name, tags), cfg.clock(), cfg.step().toMillis());
    }

    private static final String HTTP_REQUESTS = "http.requests";
    private static final String BYTES_SENT = "bytes.sent";

    @SuppressWarnings("PackageVisibleField")
    private final class RouteStats implements Comparable<RouteStats> {

        final String route;
        final MeanCounter hits;
        final MeanCounter bytesSent;

        private RouteStats(String route) {
            this.route = route;
            this.hits = newAveragingCounter(HTTP_REQUESTS, "route", route);
            this.bytesSent = newAveragingCounter(BYTES_SENT, "route", route);
        }

        @Override
        public int compareTo(RouteStats o) {
            int cmp = compare(hits.count(), o.hits.count());
            return cmp == 0 ? route.compareTo(o.route) : cmp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !getClass().equals(o.getClass())) return false;
            var stats = (RouteStats) o;
            return Objects.equals(route, stats.route);
        }

        @Override
        public int hashCode() {
            return route.hashCode();
        }

        Map<String, Object> toMap() {
            return Map.of(
              "route", route,
              "hits", hits.count(),
              "increase", hits.increase(),
              "throughput", bytesSent.mean(SECONDS)
            );
        }
    }
}
