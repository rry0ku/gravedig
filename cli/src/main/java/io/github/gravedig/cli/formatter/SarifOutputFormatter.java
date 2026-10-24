package io.github.gravedig.cli.formatter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gravedig.core.model.ScanResult;

import java.io.PrintStream;

public class SarifOutputFormatter {

    public static void print(ScanResult result, PrintStream out) {
        io.github.gravedig.core.formatter.SarifOutputFormatter.print(result, out);
    }

    public static ObjectNode buildSarifJson(ScanResult result) {
        return io.github.gravedig.core.formatter.SarifOutputFormatter.buildSarifJson(result);
    }
}
