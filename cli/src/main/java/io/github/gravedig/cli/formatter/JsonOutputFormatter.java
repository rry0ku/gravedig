package io.github.gravedig.cli.formatter;

import io.github.gravedig.core.model.ScanResult;

import java.io.PrintStream;

public class JsonOutputFormatter {

    public static void print(ScanResult result, PrintStream out) {
        io.github.gravedig.core.formatter.JsonOutputFormatter.print(result, out);
    }
}
