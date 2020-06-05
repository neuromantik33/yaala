package com.datadog.yaala.ui;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Interface for the {@link ConsoleUI} to render itself.
 *
 * @author Nicolas Estrada.
 */
public interface UIModel {

    /**
     * @return a list of log lines to display.
     */
    Iterable<String> getLogs();

    /**
     * Iterates over as many as routes as the buffer allows.
     */
    void forEachRoute(Consumer<Map<String, Object>> action);

    /**
     * @return the size of possible logs to display.
     */
    int getLogBufferSize();

    /**
     * Sets the size of possible logs to display.
     */
    void setLogBufferSize(int size);

    /**
     * Sets the size of possible statistic lines to display.
     */
    void setStatsBufferSize(int size);

    /**
     * @return the total displayable requests per second.
     */
    double totalRps();

    /**
     * @return the number of lines that couldn't be parsed.
     */
    long parseErrors();

    /**
     * @return the alert string to display.
     */
    Optional<String> getAlertString();

}
