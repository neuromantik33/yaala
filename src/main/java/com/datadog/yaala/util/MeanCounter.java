package com.datadog.yaala.util;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.step.StepDouble;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A {@link Counter} decorator wrapping a normal run-of-the-mill accumulating counter,
 * and a {@link StepDouble} using to calculate the rolling mean.
 *
 * @author Nicolas Estrada.
 */
public class MeanCounter implements Counter {

    private final Counter delegate;
    private final long stepMillis;
    private final StepDouble avg;

    public MeanCounter(Counter delegate, Clock clock, long stepMillis) {
        this.delegate = delegate;
        this.stepMillis = stepMillis;
        this.avg = new StepDouble(clock, stepMillis);
    }

    @Nonnull
    @Override
    public Id getId() {
        return delegate.getId();
    }

    @Override
    public void increment(double amount) {
        delegate.increment(amount);
        avg.getCurrent().add(amount);
    }

    @Override
    public double count() {
        return delegate.count();
    }

    /**
     * @return the increase of events which occurred within the given step.
     */
    public double increase() {
        return avg.poll();
    }

    /**
     * @param unit The base unit of time to scale the total to.
     * @return the mean rate at which events have occurred within the given step.
     */
    public double mean(TimeUnit unit) {
        return (avg.poll() / unit.convert(stepMillis, MILLISECONDS));
    }

}
