package com.datadog.yaala.ui;

import com.googlecode.lanterna.terminal.ansi.UnixTerminal;
import sun.misc.Signal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static java.lang.System.in;
import static java.lang.System.out;
import static java.nio.charset.Charset.defaultCharset;
import static sun.misc.Signal.handle;

/**
 * Patched implementation of {@link UnixTerminal} where resizing was not working during Graal native execution.
 *
 * @author Nicolas Estrada.
 */
public final class PatchedUnixTerminal extends UnixTerminal {

    private PatchedUnixTerminal(InputStream terminalInput, OutputStream terminalOutput, Charset terminalCharset)
      throws IOException {
        super(terminalInput, terminalOutput, terminalCharset);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static UnixTerminal createTerminal() throws IOException {
        return new PatchedUnixTerminal(in, out, defaultCharset());
    }

    @Override
    protected void registerTerminalResizeListener(Runnable onResize) {
        //noinspection UseOfSunClasses
        handle(new Signal("WINCH"), sig -> onResize.run());
    }
}
