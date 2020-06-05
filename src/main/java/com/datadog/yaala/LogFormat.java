package com.datadog.yaala;

import io.micrometer.core.instrument.Counter;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.datadog.yaala.LogEvent.CLF_DT_FORMAT;
import static io.micrometer.core.instrument.Metrics.counter;
import static java.lang.Integer.parseInt;
import static java.util.Optional.ofNullable;

/**
 * All http log formats should be parsed using this enumerated class.
 *
 * @author Nicolas Estrada.
 */
public enum LogFormat {

    CLF("^(?<cip>[\\S]+) - (?<ru>\\S+) \\[(?<lt>[\\w:/]+\\s[+\\-]\\d{4})] " +
      "\"(?<mth>\\w{3,4}) (?<rt>\\S+) (?<pcl>HTTP/\\d\\.\\d)\" (?<st>\\d{3}) (?<sz>\\d+)$"),

    // God awful nginx log_format!
    // log_format upstreaminfo '$the_real_ip - [$the_real_ip] - $remote_user [$time_local] "$request" $status
    // $body_bytes_sent "$http_referer" "$http_user_agent" $request_length $request_time [$proxy_upstream_name]
    // $upstream_addr $upstream_response_length $upstream_response_time $upstream_status $req_id';
    INGRESS_NGINX("^(?<cip>[\\S]+) - \\[(\\S+)] - (?<ru>\\S+) \\[(?<lt>[\\w:/]+\\s[+\\-]\\d{4})] " +
      "\"(?<mth>\\w{3,4}) (?<rt>\\S+) (?<pcl>HTTP/\\d\\.\\d)\" (?<st>\\d{3}) (?<sz>\\d+).*$");

    public static final Counter LINE_ERRORS = counter("line.errors");
    private final Pattern pattern;

    LogFormat(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    /**
     * Parses a line into a {@link LogEvent}, or returns {@link Optional#empty()}.
     */
    public Optional<LogEvent> parse(String line) {
        var matcher = pattern.matcher(line);
        LogEvent evt = null;
        if (matcher.matches()) {
            var clientIP = matcher.group("cip");
            evt = new LogEvent(
              clientIP,
              matcher.group("ru"),
              ZonedDateTime.parse(matcher.group("lt"), CLF_DT_FORMAT),
              matcher.group("mth"),
              cleanupRoute(matcher.group("rt")),
              matcher.group("pcl"),
              parseInt(matcher.group("st")),
              parseInt(matcher.group("sz")));
        } else {
            LINE_ERRORS.increment();
        }
        return ofNullable(evt);
    }

    private static String cleanupRoute(String route) {
        String clean = route;
        if (route.startsWith("http")) {
            try {
                clean = new URI(route).getPath();
            } catch (URISyntaxException ignored) {
            }
        }
        return clean;
    }
}
