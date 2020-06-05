package com.datadog.yaala.util

import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import static io.micrometer.core.instrument.simple.SimpleConfig.DEFAULT
import static java.time.Duration.ofSeconds
import static java.util.concurrent.TimeUnit.SECONDS

/**
 * @author Nicolas Estrada.
 */
class MeanCounterSpec extends Specification {

    def clock = new MockClock()
    def reg = new SimpleMeterRegistry(DEFAULT, clock)

    def 'it should maintain the average and count'() {

        given:
        def step = ofSeconds 10
        def cnt = new MeanCounter(reg.counter('test'), clock, step.toMillis())

        expect:
        cnt.count() == 0
        cnt.increase() == 0
        cnt.mean(SECONDS) == 0

        when: 'Incrementing without time moving only updates the counter, not the avg'
        cnt.increment()

        then:
        cnt.count() == 1
        cnt.increase() == 0
        cnt.mean(SECONDS) == 0

        when: 'By going to the next step we should see the average'
        clock.add step

        then:
        cnt.increase() == 1
        cnt.mean(SECONDS) == 0.1D

        when: 'Given that it\'s a rolling avg, the next step should clear it'
        clock.add step

        then:
        cnt.increase() == 0
        cnt.mean(SECONDS) == 0

    }
}
