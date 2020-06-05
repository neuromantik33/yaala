package com.datadog.yaala.util;

import io.micrometer.core.instrument.simple.SimpleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Basic {@link SimpleConfig} configuration for overriding the step size (ie. reporting frequency) to use.
 *
 * @author Nicolas Estrada.
 */
public class StepConfig implements SimpleConfig {

    private final Duration step;

    public StepConfig(Duration step) {
        this.step = step;
    }

    @Nullable
    @Override
    public String get(@Nonnull String key) {
        return null;
    }

    @Nonnull
    @Override
    public Duration step() {
        return step;
    }
}
