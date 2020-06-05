package com.datadog.yaala;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ofPattern;

/**
 * Abstraction for log events in order to collect common features from different sources/formats.
 *
 * @author Nicolas Estrada.
 */
public class LogEvent {

    private final String clientIP;
    @Nullable
    private final String remoteUser;
    private final ZonedDateTime localTime;
    private final String method;
    private final String route;
    private final String protocol;
    private final int status;
    private final int bytesSent;

    LogEvent(String clientIP, String remoteUser, ZonedDateTime localTime,
             String method, String route, String protocol,
             int status, int bytesSent) {
        this.clientIP = clientIP;
        this.remoteUser = "-".equals(remoteUser) ? null : remoteUser;
        this.localTime = localTime;
        this.method = method;
        this.route = route;
        this.protocol = protocol;
        this.status = status;
        this.bytesSent = bytesSent;
    }

    public String getClientIP() {
        return clientIP;
    }

    @Nullable
    public String getRemoteUser() {
        return remoteUser;
    }

    public ZonedDateTime getLocalTime() {
        return localTime;
    }

    public String getMethod() {
        return method;
    }

    public String getRoute() {
        return route;
    }

    public String getProtocol() {
        return protocol;
    }

    public int getStatus() {
        return status;
    }

    public int getBytesSent() {
        return bytesSent;
    }

    @Override
    public String toString() {
        return format("%s - %s [%s] \"%s %s %s\" %d %d",
          clientIP,
          remoteUser != null ? remoteUser : "-",
          localTime.format(CLF_DT_FORMAT),
          method,
          route,
          protocol,
          status,
          bytesSent);
    }

    static final DateTimeFormatter CLF_DT_FORMAT = ofPattern("dd/LLL/yyyy:HH:mm:ss Z");

}
