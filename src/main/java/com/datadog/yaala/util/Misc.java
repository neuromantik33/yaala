package com.datadog.yaala.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static java.lang.Math.round;
import static java.lang.String.format;
import static java.nio.charset.Charset.defaultCharset;

/**
 * Some useful functions that don't have a home yet. :P
 *
 * @author Nicolas Estrada.
 */
public final class Misc {

    private Misc() {
    }

    /**
     * Convenience method for creating a {@link BufferedReader}.
     */
    public static BufferedReader newReader(Path path) throws IOException {
        return new BufferedReader(new FileReader(path.toFile(), defaultCharset()));
    }

    /**
     * Given a throughput, prints a human readable format for it.
     */
    public static String printBandwidth(double throughput) {
        long n = 2 << 9;
        var s = "";
        double kb = throughput / n;
        double mb = kb / n;
        double gb = mb / n;
        if (throughput < n) {
            s = format("%d bps", round(throughput));
        } else if (throughput >= n && throughput < (n * n)) {
            s = format("%d KB/s", round(kb));
        } else if (throughput >= (n * n) && throughput < (n * n * n)) {
            s = format("%d MB/s", round(mb));
        } else if (throughput >= (n * n * n)) {
            s = format("%d GB/s", round(gb));
        }
        return s;
    }
}
