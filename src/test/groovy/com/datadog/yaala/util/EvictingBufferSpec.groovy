package com.datadog.yaala.util

import spock.lang.Specification

import static com.datadog.yaala.util.EvictingBuffer.DEFAULT_CAPACITY

/**
 * @author Nicolas Estrada.
 */
class EvictingBufferSpec extends Specification {

    def 'the evicting buffer\'s size should always be less than its capacity, evicting older elements if necessary'() {

        given:
        def buf = new EvictingBuffer(DEFAULT_CAPACITY)
        10.times { buf.add it }

        expect:
        buf.toList() == (0..9) as List

        when:
        buf.add 10

        then:
        buf.toList() == (1..10) as List

    }
}
