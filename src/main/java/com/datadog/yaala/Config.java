package com.datadog.yaala;

import io.micrometer.core.instrument.Clock;

import java.time.Duration;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

/**
 * Easy way to group all configuration details in a single shared API in lack of dependency injection.
 *
 * @author Nicolas Estrada.
 */
public interface Config {

    /**
     * @return the log format to use (default is {@link LogFormat#CLF}).
     */
    default LogFormat logFormat() {
        return LogFormat.CLF;
    }

    /**
     * @return the UI refresh period in milliseconds (default is 200ms);
     */
    default long refreshPeriodMs() {
        //noinspection MagicNumber
        return 250L;
    }

    /**
     * @return the depth at which to truncate routes into sections (default is 1).
     */
    default int routeDepth() {
        return 1;
    }

    /**
     * @return the rate of total requests per second at which point an alert will be displayed (default is 10 rps).
     */
    default int alertThreshold() {
        return 10;
    }

    /**
     * @return the {@link Clock} used to calculate rates (used primarily for testing).
     */
    default Clock clock() {
        return Clock.SYSTEM;
    }

    /**
     * @return the step size (ie. reporting frequency) to use. Primarily for calculating means/avgs
     * (used primarily for testing; default is 10s)
     */
    default Duration step() {
        return ofSeconds(10);
    }

    /**
     * @return the delay to wait until an alert is fired from too many requests per second (default is 2m).
     */
    default Duration alertDelay() {
        return ofMinutes(2);
    }

    /**
     * @return the cooldown period to wait after an alert is triggered to remove the alert
     * in order to avoid <i>thrashing</i> (default is 2m).
     */
    default Duration alertCooldown() {
        return ofMinutes(2);
    }
}
