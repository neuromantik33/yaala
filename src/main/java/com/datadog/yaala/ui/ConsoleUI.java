package com.datadog.yaala.ui;

import com.datadog.yaala.Config;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.graphics.TextGraphicsWriter;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;

import static com.datadog.yaala.util.Misc.printBandwidth;
import static com.googlecode.lanterna.SGR.BLINK;
import static com.googlecode.lanterna.SGR.BOLD;
import static com.googlecode.lanterna.Symbols.DOUBLE_LINE_HORIZONTAL;
import static com.googlecode.lanterna.TextColor.ANSI.BLUE;
import static com.googlecode.lanterna.TextColor.ANSI.DEFAULT;
import static com.googlecode.lanterna.TextColor.ANSI.YELLOW;
import static com.googlecode.lanterna.input.KeyType.Escape;
import static com.googlecode.lanterna.screen.Screen.RefreshType.COMPLETE;
import static com.googlecode.lanterna.screen.Screen.RefreshType.DELTA;
import static com.googlecode.lanterna.screen.WrapBehaviour.CLIP;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.graalvm.nativeimage.ImageInfo.inImageCode;

/**
 * This class is responsable for rendering all the related logs, statistics and alerts into the console.
 * <br>
 * It implements a naive bootstrap-like grid, the screen is divided into {@link ConsoleUI#NUM_PARTS} parts,
 * and all statistics save the <i>route</i> take up 1 part, which leaves 3 other parts for <i>hits</i>, <i>threshold</i>
 * and <i>increase</i>.
 * <br>
 * It is very procedural and somewhat naive implementation however it gets the job done and perhaps could use a
 * cosmetic makeover UX wise..
 *
 * @author Nicolas Estrada.
 */
@SuppressWarnings({"StringConcatenation", "DuplicateStringLiteralInspection"})
public final class ConsoleUI implements AutoCloseable {

    private final Screen screen;
    private final long refreshPeriodNs;
    private final UIModel model;
    private final TextGraphics textGraphics;
    private final TextGraphicsWriter logWriter;

    private TerminalSize size;
    private long lastUiRefresh = nanoTime();
    private String statsFormat;

    private ConsoleUI(Screen screen, long refreshPeriodMs, UIModel model) {
        this.screen = screen;
        this.refreshPeriodNs = MILLISECONDS.toNanos(refreshPeriodMs);
        this.model = model;
        this.textGraphics = screen.newTextGraphics();
        this.logWriter = new TextGraphicsWriter(textGraphics);
        logWriter.setWrapBehaviour(CLIP);
    }

    /**
     * Initializes the Console ui.
     *
     * @param cfg   the config.
     * @param model the data layer for this UI.
     * @return the newly created ConsoleUI instance.
     */
    public static ConsoleUI initConsole(Config cfg, UIModel model) throws IOException {
        var term = initTerminal();
        var screen = initScreen(term);
        var console = new ConsoleUI(screen, cfg.refreshPeriodMs(), model);
        console.size = screen.getTerminalSize();
        console.resizeLogsBuffer();
        console.resizeStatsBuffer();
        return console;
    }

    /**
     * Refreshes the UI but limiting refreshes to only every <i>refreshPeriodNs</i> nanos.
     *
     * @see Screen#refresh()
     */
    public void refresh() throws IOException {
        boolean resize = shouldResize();
        long now = nanoTime();
        if (resize || (now - lastUiRefresh > refreshPeriodNs)) {
            updateTotal();
            updateLogs();
            updateRouteStats();
            screen.refresh(resize ? COMPLETE : DELTA);
            lastUiRefresh = now;
        }
    }

    /**
     * If a new screen size is detected, trigger a {@code COMPLETE} refresh, otherwise simply refresh the {@code DELTA}.
     */
    private boolean shouldResize() {
        var newSize = screen.doResizeIfNecessary();
        if (newSize == null) {
            return false;
        }
        size = newSize;
        resizeLogsBuffer();
        resizeStatsBuffer();
        return true;
    }

    /**
     * @return {@code true} if the user requests an exit (hitting ESC), {@code false} otherwise.
     */
    public boolean shouldExit() throws IOException {
        var keyStroke = screen.pollInput();
        return keyStroke != null && keyStroke.getKeyType() == Escape;
    }

    private void resizeLogsBuffer() {
        int sz = (size.getRows() >> 1) - 1;
        model.setLogBufferSize(sz);
    }

    private void resizeStatsBuffer() {
        int sz = (size.getRows() >> 1) - START_Y_STATS - 2;
        model.setStatsBufferSize(sz);
        // Adjust the stats format
        int partSz = (size.getColumns() - 1) / NUM_PARTS;
        // FIXME Super ugly optimization due to hasty attempt to have fast resizable columns
        statsFormat = MessageFormat.format(" %1$-{0}s%2$-{1}s%3$-{1}s%4$-{1}s%n", partSz << 1, partSz);
    }

    private void updateTotal() {
        textGraphics.putString(0, START_Y_TOTAL, format("%1$-" + size.getColumns() + "s", " "));
        var s = format("Http requests per second: %.2f rps (errors=%d)", model.totalRps(), model.parseErrors());
        textGraphics.putString(1, START_Y_TOTAL, s, BOLD);
        model
          .getAlertString()
          .ifPresentOrElse(this::writeAlert, this::clearAlert);
    }

    private void writeAlert(String msg) {
        textGraphics.setForegroundColor(YELLOW);
        textGraphics.setBackgroundColor(BLUE);
        textGraphics.putString(size.getColumns() - msg.length(), START_Y_TOTAL, msg, BOLD, BLINK);
        textGraphics.setForegroundColor(DEFAULT);
        textGraphics.setBackgroundColor(DEFAULT);
    }

    private void clearAlert() {
        int halfX = size.getColumns() >> 1;
        textGraphics.putString(halfX, START_Y_TOTAL, format("%1$" + (halfX - 1) + "s", ""));
    }

    private void updateRouteStats() {
        var header = format(statsFormat, "route", "║ hits", "║ increase", "║ throughput");
        textGraphics.putString(0, START_Y_STATS, header, BOLD);
        drawLineY(START_Y_STATS + 1);
        logWriter.setCursorPosition(new TerminalPosition(0, START_Y_STATS + 2));
        model.forEachRoute(stats -> {
            var line = format(statsFormat,
              stats.get("route"),
              formatHits(stats),
              formatIncrease(stats),
              formatThroughput(stats)
            );
            logWriter.putString(line);
        });
    }

    private static String formatHits(Map<String, Object> stats) {
        var hits = (Double) stats.get("hits");
        return "| " + hits.longValue();
    }

    private static String formatIncrease(Map<String, Object> stats) {
        var increase = (Double) stats.get("increase");
        return increase == 0.0 ? "| -" : format("| +%d", increase.longValue());
    }

    private static String formatThroughput(Map<String, Object> stats) {
        var throughput = (Double) stats.get("throughput");
        return throughput == 0.0 ? "| -" : "| " + printBandwidth(throughput);
    }

    private void updateLogs() {
        int halfY = size.getRows() >> 1;
        drawLineY(halfY);
        logWriter.setCursorPosition(new TerminalPosition(0, halfY + 1));
        model.getLogs().forEach(logWriter::putString);
    }

    private void drawLineY(int y) {
        textGraphics.drawLine(0, y, size.getColumns(), y, DOUBLE_LINE_HORIZONTAL);
    }

    @Override
    public void close() throws IOException {
        screen.close();
    }

    /**
     * @return a {@link Terminal}. If building a Graal native image, it should always create a <i>headless</i> one.
     */
    private static Terminal initTerminal() throws IOException {
        if (inImageCode()) {
            return PatchedUnixTerminal.createTerminal();
        } else {
            return new DefaultTerminalFactory().createTerminal();
        }
    }

    private static TerminalScreen initScreen(Terminal term) throws IOException {
        var screen = new TerminalScreen(term);
        screen.startScreen();
        screen.setCursorPosition(null);
        return screen;
    }

    private static final int NUM_PARTS = 5;
    private static final int START_Y_TOTAL = 1;
    private static final int START_Y_STATS = START_Y_TOTAL + 3;
}
