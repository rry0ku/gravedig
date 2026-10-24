package io.github.gravedig.core.formatter;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.ScanResult;

import java.io.PrintStream;
import java.io.StringWriter;

public class CsvOutputFormatter {

    public static String format(ScanResult result) {
        StringWriter sw = new StringWriter();
        sw.write("id,rule_id,rule_name,severity,status,presence,file_path,line_number,commit_hash,author,commit_date,entropy,validation_reason,snippet\n");

        if (result != null && result.getFindings() != null) {
            for (Finding f : result.getFindings()) {
                sw.write(escapeCsv(f.getId()) + ",");
                sw.write(escapeCsv(f.getRuleId()) + ",");
                sw.write(escapeCsv(f.getRuleName()) + ",");
                sw.write(escapeCsv(f.getSeverity().name()) + ",");
                sw.write(escapeCsv(f.getStatus() != null ? f.getStatus().name() : "OPEN") + ",");
                sw.write(escapeCsv(f.getPresence() != null ? f.getPresence().name() : "ALIVE") + ",");
                sw.write(escapeCsv(f.getFilePath()) + ",");
                sw.write(f.getLineNumber() + ",");
                sw.write(escapeCsv(f.getCommitHash() != null ? f.getCommitHash() : "") + ",");
                sw.write(escapeCsv(f.getCommitAuthor() != null ? f.getCommitAuthor() : "") + ",");
                sw.write(escapeCsv(f.getCommitDate() != null ? f.getCommitDate().toString() : "") + ",");
                sw.write(f.getEntropy() + ",");
                sw.write(escapeCsv(f.getValidationReason() != null ? f.getValidationReason() : "") + ",");
                sw.write(escapeCsv(f.getSnippetRedacted() != null ? f.getSnippetRedacted() : "") + "\n");
            }
        }
        return sw.toString();
    }

    public static void print(ScanResult result, PrintStream out) {
        out.print(format(result));
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
