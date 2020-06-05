package com.datadog.yaala;

import com.datadog.yaala.ui.UIModel;
import com.datadog.yaala.util.EvictingBuffer;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneId.systemDefault;

/**
 * Default implementation of the {@link UIModel}.
 */
public class DefaultUIModel implements UIModel {

    private final Config cfg;
    private final LogStatistics statistics;
    private final EvictingBuffer<String> logs;

    private int maxStats;

    DefaultUIModel(Config cfg) {
        this.cfg = cfg;
        this.statistics = new LogStatistics(cfg);
        this.logs = new EvictingBuffer<>();
    }

    @Override
    public Iterable<String> getLogs() {
        return logs;
    }

    @Override
    public void forEachRoute(Consumer<Map<String, Object>> action) {
        var stats = statistics.getRouteStatistics();
        int i = 0;
        for (var rs : stats) {
            if (i == maxStats) {
                break;
            }
            action.accept(rs);
            i++;
        }
    }

    @Override
    public int getLogBufferSize() {
        return logs.getCapacity();
    }

    @Override
    public void setLogBufferSize(int size) {
        logs.setCapacity(size);
    }

    @Override
    public void setStatsBufferSize(int size) {
        maxStats = size;
    }

    @Override
    public double totalRps() {
        return statistics.totalRps();
    }

    @Override
    public long parseErrors() {
        //noinspection NumericCastThatLosesPrecision
        return (long) LogFormat.LINE_ERRORS.count();
    }

    @Override
    public Optional<String> getAlertString() {
        return statistics
          .getAlertTriggerTime()
          .map(e -> {
              var wallTime = e.getKey();
              var rps = e.getValue();
              var ldt = ofEpochMilli(wallTime).atZone(systemDefault()).toLocalDateTime();
              return format("High traffic generated an alert: rps=%.2f, triggered at %s", rps, ldt);
          });
    }

    /**
     * Processes the <i>lines</i> and update the internal statistics and logs.
     */
    void processLines(Iterable<String> lines) {
        for (var line : lines) {
            cfg
              .logFormat()
              .parse(line)
              .ifPresentOrElse(statistics::ingest, statistics::incRequests);
            logs.add(format("%s%n", line));
        }
        statistics.refreshAlert();
    }
}
