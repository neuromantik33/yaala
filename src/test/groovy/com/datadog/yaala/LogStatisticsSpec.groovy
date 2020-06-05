package com.datadog.yaala

import io.micrometer.core.instrument.MockClock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.ZonedDateTime

import static com.datadog.yaala.LogStatistics.routeSection
import static java.time.Duration.ofSeconds
import static java.time.Instant.ofEpochMilli
import static java.time.ZoneId.systemDefault as defaultTz

/**
 * @author Nicolas Estrada.
 */
class LogStatisticsSpec extends Specification {

    def step = ofSeconds 1
    def clock = new MockClock()
    def cfg = [
      clock        : { clock },
      step         : { step },
      // set the delay and cooldown periods to more testable amounts
      alertDelay   : { ofSeconds(10) },
      alertCooldown: { ofSeconds(5) }
    ] as Config

    @Subject
    def stats = new LogStatistics(cfg)

    void setup() {
        LogStatistics.metaClass.leftShift = {
            delegate.ingest it
        }
    }

    def 'it should collect some basic stats for each route'() {

        expect:
        stats.totalRps() == 0
        stats.routeStatistics == []

        when:
        stats << emitEvent(route: '/api/user')
        stats << emitEvent(route: '/api/user')
        stats << emitEvent(route: '/report', bytesSent: 500)
        clock.add step

        then:
        stats.totalRps() == 3
        stats.routeStatistics == [
          [route: '/api', hits: 2.0, throughput: 200.0D, increase: 2],
          [route: '/report', hits: 1.0, throughput: 500.0D, increase: 1]
        ]

        when: 'Next step means should drop'
        clock.add step

        then:
        stats.totalRps() == 0
        stats.routeStatistics == [
          [route: '/api', hits: 2.0, throughput: 0, increase: 0],
          [route: '/report', hits: 1.0, throughput: 0, increase: 0]
        ]
    }

    def 'it the rps threshold is reached for longer than 10 seconds, it should alert'() {

        def simulateTraffic = {
            10.times { stats << emitEvent() }
            clock.add step
            stats.refreshAlert()
            assert stats.totalRps() == 10
            clock.wallTime()
        }

        when: 'default threshold is 10 rps, so for a step of 1s, 10 events should be ingested for the alert to maybe trigger'
        def alertTime = simulateTraffic()

        then: 'the alert has not yet triggered since the delay has not elapsed'
        stats.totalRps() == 10
        !stats.alertTriggerTime.present

        when: 'sustain the traffic and advance the delay'
        10.times { simulateTraffic() }

        then:
        stats.totalRps() == 10
        stats.alertTriggerTime.present
        stats.alertTriggerTime.get().key == alertTime
        stats.alertTriggerTime.get().value == 10.0

        when: 'No more traffic is being sent, but the alert should always be present until cooldown period is reached'
        4.times {
            clock.add step
            stats.refreshAlert()
            assert stats.totalRps() == 0
        }

        then:
        stats.alertTriggerTime.present
        stats.alertTriggerTime.get().key == alertTime
        stats.alertTriggerTime.get().value == 10.0

        when:
        clock.add step
        stats.refreshAlert()

        then: 'No more alert ;)'
        stats.totalRps() == 0
        !stats.alertTriggerTime.present

    }

    @Unroll
    def 'the section for route "#route" with depth #depth is "#section"'() {

        expect:
        routeSection(route, depth) == section

        where:
        route             | depth | section
        '/api/user'       | 0     | '/'
        '/api/user'       | 1     | '/api'
        '/report'         | 1     | '/report'
        '/report'         | 2     | '/report'
        '/api/user/bob'   | 1     | '/api'
        '/api/user/bob'   | 2     | '/api/user'
        '/api/user/bob'   | 3     | '/api/user/bob'
        '/login?user=bob' | 1     | '/login' // Cleanup any query params

    }

    def emitEvent(Map opts = [:]) {
        new LogEvent(opts.ip ?: '127.0.0.1',
                     opts.user ?: 'nobody',
                     opts.localTime ?: localTimeAt(),
                     opts.method ?: 'GET',
                     opts.route ?: '/api',
                     opts.protocol ?: 'HTTP/1.1',
                     opts.status ?: 200,
                     opts.bytesSent ?: 100)
    }

    ZonedDateTime localTimeAt() {
        def now = clock.wallTime()
        ZonedDateTime.ofInstant ofEpochMilli(now), defaultTz()
    }
}
