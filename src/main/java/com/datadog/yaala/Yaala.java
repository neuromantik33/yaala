package com.datadog.yaala;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static com.datadog.yaala.ui.ConsoleUI.initConsole;
import static com.datadog.yaala.util.Misc.newReader;
import static java.lang.System.exit;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.time.Duration.ofSeconds;

/**
 * Main entry point.
 *
 * @author Nicolas Estrada.
 */
@Command(
  name = "yaala",
  mixinStandardHelpOptions = true,
  description = "Follows an HTTP log file and gathers useful metrics from the incoming requests",
  showDefaultValues = true
)
public final class Yaala implements Callable<Integer>, Config {

    @Parameters(
      paramLabel = "LOG_PATH",
      description = "The path to the HTTP access log file",
      defaultValue = "/tmp/access.log"
    )
    private Path logPath;

    @Option(
      names = {"-f", "--format"},
      description = "The log format to use",
      defaultValue = "CLF"
    )
    private LogFormat logFormat;

    @Option(
      names = "--ui-refresh",
      description = "the UI refresh period in milliseconds",
      defaultValue = "250"
    )
    private long refreshPeriodMs;

    @Option(
      names = {"-d", "--route-depth"},
      description = "The depth at which to truncate routes into sections",
      defaultValue = "1"
    )
    private Integer routeDepth;

    @Option(
      names = {"-a", "--alert-threshold"},
      description = "The rate of total requests per second at which point an alert will be displayed",
      defaultValue = "10"
    )
    private Integer alertThreshold;

    @Option(
      names = "--alert-delay",
      description = "The delay in seconds to wait until an alert is fired from too many requests per second",
      defaultValue = "120"
    )
    private int alertDelay;

    @Option(
      names = "--alert-cooldown",
      description = "The cooldown period in seconds to wait after an alert is triggered to remove the alert in order to avoid thrashing",
      defaultValue = "120"
    )
    private int alertCooldown;

    public static void main(String... args) {
        int rc = new CommandLine(new Yaala()).execute(args);
        exit(rc);
    }

    @Override
    public LogFormat logFormat() {
        return logFormat;
    }

    @Override
    public long refreshPeriodMs() {
        return refreshPeriodMs;
    }

    @Override
    public int routeDepth() {
        return routeDepth;
    }

    @Override
    public int alertThreshold() {
        return alertThreshold;
    }

    @Override
    public Duration alertDelay() {
        return ofSeconds(alertDelay);
    }

    @Override
    public Duration alertCooldown() {
        return ofSeconds(alertCooldown);
    }

    @Override
    public Integer call() {
        int rc = 0;
        var driver = currentThread();
        var model = new DefaultUIModel(this);
        try (var logFile = newReader(logPath);
             var console = initConsole(this, model)) {
            while (!driver.isInterrupted()) {
                if (console.shouldExit()) {
                    break;
                }
                var lines = readLines(logFile, model.getLogBufferSize());
                if (lines.isEmpty()) {
                    //noinspection BusyWait
                    sleep(refreshPeriodMs());
                } else {
                    model.processLines(lines);
                }
                console.refresh();
            }
        } catch (IOException e) {
            e.printStackTrace();
            rc = 1;
        } catch (InterruptedException e) {
            driver.interrupt();
        }
        return rc;
    }

    private static List<String> readLines(BufferedReader file, int n) throws IOException {
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var line = file.readLine();
            if (line == null) {
                break;
            } else {
                lines.add(line);
            }
        }
        return lines;
    }
}
