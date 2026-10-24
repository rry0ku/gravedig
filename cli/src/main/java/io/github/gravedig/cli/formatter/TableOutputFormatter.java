package io.github.gravedig.cli.formatter;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;

import java.io.PrintStream;
import java.util.List;

public class TableOutputFormatter {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String SAND = "\u001B[38;5;187m"; // #d9c9a3 bone/sand

    public static void print(ScanResult result, PrintStream out) {
        boolean useAnsi = System.console() != null || "true".equalsIgnoreCase(System.getenv("FORCE_COLOR"));

        printBanner(out, useAnsi);

        ScanSummary summary = result.getSummary();
        out.println(color(BOLD, useAnsi) + "Target: " + color(RESET, useAnsi) + result.getTargetPath());
        out.println(String.format("Files Scanned: %s%d%s  |  Commits Scanned: %s%d%s  |  Duration: %s%d ms%s  |  Findings: %s",
                color(BOLD, useAnsi), summary.getFilesScanned(), color(RESET, useAnsi),
                color(BOLD, useAnsi), summary.getCommitsScanned(), color(RESET, useAnsi),
                color(BOLD, useAnsi), summary.getDurationMs(), color(RESET, useAnsi),
                summary.getTotalFindings() > 0
                        ? (color(RED + BOLD, useAnsi) + summary.getTotalFindings() + color(RESET, useAnsi))
                        : "0"
        ));
        out.println();

        List<Finding> findings = result.getFindings();
        if (findings.isEmpty()) {
            out.println(color(GREEN + BOLD, useAnsi) + "✓ No secrets detected! The graveyard is quiet." + color(RESET, useAnsi));
            out.println();
            return;
        }

        int sevWidth = 10;
        int riskWidth = 6;
        int ruleWidth = 24;
        int locWidth = 28;
        int secretWidth = 24;
        int commitWidth = 10;
        int exposureWidth = 14;
        int presenceWidth = 8;
        int statusWidth = 13;

        String border = "─".repeat(sevWidth + riskWidth + ruleWidth + locWidth + secretWidth + commitWidth + exposureWidth + presenceWidth + statusWidth + 24);
        out.println(border);
        out.println(String.format(" %-10s │ %-6s │ %-24s │ %-28s │ %-24s │ %-10s │ %-14s │ %-8s │ %-13s",
                "SEVERITY", "RISK", "RULE", "LOCATION", "SECRET (REDACTED)", "COMMIT", "EXPOSURE", "PRESENCE", "STATUS"));
        out.println(border);

        for (Finding f : findings) {
            String sevStr = colorSeverity(f.getSeverity(), useAnsi);
            String riskStr = colorRisk(f.getRiskScore(), useAnsi);
            String ruleStr = truncate(f.getRuleId(), ruleWidth);
            String locStr = truncate(f.getFilePath() + ":" + f.getLineNumber(), locWidth);
            String secretStr = truncate(f.getSecretRedacted(), secretWidth);

            String commitStr = f.getCommitHash() != null && !f.getCommitHash().isEmpty() ? f.getCommitHash() : "HEAD";
            commitStr = truncate(commitStr, commitWidth);

            String exposureStr = "-";
            if (f.getExposureWindow() != null) {
                exposureStr = f.getExposureWindow().getDurationFormatted();
            }
            exposureStr = truncate(exposureStr, exposureWidth);

            String presenceStr = f.getPresence().name();
            String coloredPresence = "ALIVE".equals(presenceStr)
                    ? (color(RED + BOLD, useAnsi) + presenceStr + color(RESET, useAnsi))
                    : (color(DIM, useAnsi) + presenceStr + color(RESET, useAnsi));

            String statusStr = f.getStatus().name();
            String coloredStatus = f.getStatus() == FindingStatus.ACCEPTED_RISK
                    ? (color(YELLOW, useAnsi) + statusStr + color(RESET, useAnsi))
                    : statusStr;

            out.println(String.format(" %-19s │ %-15s │ %-24s │ %-28s │ %-24s │ %-10s │ %-14s │ %s │ %s",
                    sevStr, riskStr, ruleStr, locStr, secretStr, commitStr, exposureStr, coloredPresence, coloredStatus));
        }
        out.println(border);
        out.println();

        out.println(String.format("Summary: %sCRITICAL: %d%s │ %sHIGH: %d%s │ %sMEDIUM: %d%s │ %sLOW: %d%s  (%sALIVE: %d%s, %sBURIED: %d%s)",
                color(RED + BOLD, useAnsi), summary.getCountForSeverity(Severity.CRITICAL), color(RESET, useAnsi),
                color(YELLOW + BOLD, useAnsi), summary.getCountForSeverity(Severity.HIGH), color(RESET, useAnsi),
                color(CYAN, useAnsi), summary.getCountForSeverity(Severity.MEDIUM), color(RESET, useAnsi),
                color(DIM, useAnsi), summary.getCountForSeverity(Severity.LOW), color(RESET, useAnsi),
                summary.getAliveFindings() > 0 ? (color(RED + BOLD, useAnsi)) : "",
                summary.getAliveFindings(),
                color(RESET, useAnsi),
                color(DIM, useAnsi),
                summary.getBuriedFindings(),
                color(RESET, useAnsi)
        ));
        out.println();
    }

    private static void printBanner(PrintStream out, boolean useAnsi) {
        out.println(color(SAND + BOLD, useAnsi) +
                "  ____                                 _ _       \n" +
                " / ___|_ __ __ ___   _____  __| (_) __ _ \n" +
                "| |  _| '__/ _` \\ \\ / / _ \\/ _` | |/ _` |\n" +
                "| |_| | | | (_| |\\ V /  __/ (_| | | (_| |\n" +
                " \\____|_|  \\__,_| \\_/ \\___|\\__,_|_|\\__, |\n" +
                "                                   |___/ " + color(RESET, useAnsi));
        out.println(color(DIM, useAnsi) + " \"Dig up the secrets your git history forgot to bury.\"" + color(RESET, useAnsi));
        out.println();
    }

    private static String colorSeverity(Severity severity, boolean useAnsi) {
        if (!useAnsi) {
            return String.format("%-10s", severity.name());
        }
        String colorCode;
        switch (severity) {
            case CRITICAL: colorCode = RED + BOLD; break;
            case HIGH: colorCode = YELLOW + BOLD; break;
            case MEDIUM: colorCode = CYAN; break;
            case LOW:
            default: colorCode = DIM; break;
        }
        return colorCode + String.format("%-10s", severity.name()) + RESET;
    }

    private static String colorRisk(int score, boolean useAnsi) {
        String formatted = String.format("%d/100", score);
        if (!useAnsi) return String.format("%-6s", formatted);

        String colorCode;
        if (score >= 80) colorCode = RED + BOLD;
        else if (score >= 60) colorCode = YELLOW + BOLD;
        else if (score >= 40) colorCode = CYAN;
        else colorCode = DIM;

        return colorCode + String.format("%-6s", formatted) + RESET;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return "..." + text.substring(text.length() - (maxLen - 3));
    }

    private static String color(String code, boolean useAnsi) {
        return useAnsi ? code : "";
    }
}
