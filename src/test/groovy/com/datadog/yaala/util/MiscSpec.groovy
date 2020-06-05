package com.datadog.yaala.util

import spock.lang.Specification
import spock.lang.Unroll

import static Misc.printBandwidth

/**
 * @author Nicolas Estrada.
 */
class MiscSpec extends Specification {

    @Unroll
    def 'it should print #rate for throughput #tp'() {

        expect:
        printBandwidth(tp) == rate

        where:
        tp       | rate
        16       | '16 bps'
        2048     | '2 KB/s'
        10000000 | '10 MB/s'

    }
}
