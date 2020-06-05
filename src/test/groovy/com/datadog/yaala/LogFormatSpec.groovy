package com.datadog.yaala

import spock.lang.Specification
import spock.lang.Unroll

import static com.datadog.yaala.LogFormat.CLF
import static com.datadog.yaala.LogFormat.INGRESS_NGINX
import static java.time.ZonedDateTime.parse as zdt

/**
 * @author Nicolas Estrada.
 */
class LogFormatSpec extends Specification {

    @Unroll
    def 'the clf log line #line should be a valid log event'() {

        given:
        def optEvt = CLF.parse line

        expect:
        optEvt.present

        when:
        def evt = optEvt.get()

        then:
        verifyAll {
            evt.clientIP == clientIP
            evt.remoteUser == remoteUser
            evt.localTime == zdt(localTime)
            evt.method == method
            evt.route == route
            evt.protocol == protocol
            evt.status == status
            evt.bytesSent == bytesSent
        }

        where:
        clientIP    | remoteUser | localTime              | method | route       | protocol   | status | bytesSent
        '127.0.0.1' | 'james'    | '2018-05-09T16:00:39Z' | 'GET'  | '/report'   | 'HTTP/1.0' | 200    | 123
        '127.0.0.1' | 'jill'     | '2018-05-09T16:00:41Z' | 'GET'  | '/api/user' | 'HTTP/1.0' | 200    | 234
        '127.0.0.1' | 'frank'    | '2018-05-09T16:00:42Z' | 'POST' | '/api/user' | 'HTTP/1.0' | 200    | 34
        '127.0.0.1' | 'mary'     | '2018-05-09T16:00:42Z' | 'POST' | '/api/user' | 'HTTP/1.0' | 503    | 12

        line << [
          '127.0.0.1 - james [09/May/2018:16:00:39 +0000] "GET /report HTTP/1.0" 200 123',
          '127.0.0.1 - jill [09/May/2018:16:00:41 +0000] "GET /api/user HTTP/1.0" 200 234',
          '127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34',
          '127.0.0.1 - mary [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 503 12'
        ]
    }

    @Unroll
    def 'the nginx log line "#line" should be a valid log event'() {

        given:
        def optEvt = INGRESS_NGINX.parse line

        expect:
        optEvt.present

        when:
        def evt = optEvt.get()

        then:
        verifyAll {
            evt.clientIP == clientIP
            evt.remoteUser == remoteUser
            evt.localTime == zdt(localTime)
            evt.method == method
            evt.route == route
            evt.protocol == protocol
            evt.status == status
            evt.bytesSent == bytesSent
        }

        where:
        clientIP         | remoteUser | localTime              | method | route                                                                                                                                                                              | protocol   | status | bytesSent
        '213.218.143.17' | null       | '2020-05-31T14:57:26Z' | 'POST' | '/api/curiosity/v1/article-availabilities'                                                                                                                                         | 'HTTP/1.1' | 200    | 83
        '213.218.143.21' | null       | '2020-06-04T16:09:14Z' | 'GET'  | '/api/datasources/proxy/1/api/v1/query_range?query=sum%20by%20(wid)%20(rate(incoming_messages_total%7Bwid%3D%221%22%7D%5B5m%5D)%20*%2060)&start=1591286655&end=1591286955&step=15' | 'HTTP/2.0' | 200    | 229
        '5.188.210.101'  | null       | '2020-06-05T07:42:52Z' | 'GET'  | '/echo.php'                                                                                                                                                                        | 'HTTP/1.1' | 400    | 658

        line << [
          '213.218.143.17 - [213.218.143.17] - - [31/May/2020:14:57:26 +0000] "POST /api/curiosity/v1/article-availabilities HTTP/1.1" 200 83 "-" "Apache-HttpAsyncClient/4.1.4 (Java/11.0.3)" 366 0.007 [curiosity-curiosity-api-8080] 10.28.7.60:8080 52 0.007 200 36adb866762c34d934d37850a4a95635',
          '213.218.143.21 - [213.218.143.21] - - [04/Jun/2020:16:09:14 +0000] "GET /api/datasources/proxy/1/api/v1/query_range?query=sum%20by%20(wid)%20(rate(incoming_messages_total%7Bwid%3D%221%22%7D%5B5m%5D)%20*%2060)&start=1591286655&end=1591286955&step=15 HTTP/2.0" 200 229 "https://monitoring.cl06-prod.osca.ro/d/We50S3yZk/dashboard-curiosity-copy?orgId=1&refresh=5s&from=now-5m&to=now&var-instance=All&var-pod=All&var-wid=1&var-pool=All&var-route=All&var-category=All&var-interval=$__auto_interval_interval" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36" 154 0.005 [monitoring-grafana-3000] 10.28.4.5:3000 229 0.006 200 0da6ff0a902779b0a68fc6e5adc13245',
          '5.188.210.101 - [5.188.210.101] - - [05/Jun/2020:07:42:52 +0000] "GET http://5.188.210.101/echo.php HTTP/1.1" 400 658 "https://www.google.com/" "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36" 361 0.000 [] - - - - 4beaa1b7059ddd4f568fc3aeae388187'
        ]
    }
}
