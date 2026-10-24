package io.github.gravedig.cli.formatter;

import io.github.gravedig.core.model.ScanResult;

import java.io.PrintStream;

public class HtmlReportFormatter {

    public static void print(ScanResult result, PrintStream out) {
        io.github.gravedig.core.formatter.HtmlReportFormatter.print(result, out);
    }
}
