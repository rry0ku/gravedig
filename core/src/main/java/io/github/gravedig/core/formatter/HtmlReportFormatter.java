package io.github.gravedig.core.formatter;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

public class HtmlReportFormatter {

    public static String format(ScanResult result) {
        ScanSummary s = result != null && result.getSummary() != null ? result.getSummary() :
                new ScanSummary(0, 0, 0, 0, 0, 0, Collections.emptyMap());
        List<Finding> findings = result != null && result.getFindings() != null ? result.getFindings() : Collections.emptyList();
        String target = result != null ? result.getTargetPath() : "unknown";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>gravedig Scan Report</title>\n");
        html.append("<style>\n");
        html.append(":root { --bg: #0e1012; --panel: #16191c; --border: #23282d; --sand: #d9c9a3; --text: #e6edf3; --muted: #8b949e; --red: #f85149; --amber: #d29922; --blue: #58a6ff; }\n");
        html.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("body { background: var(--bg); color: var(--text); font-family: Inter, -apple-system, sans-serif; line-height: 1.5; padding: 32px 24px; }\n");
        html.append("header { border-bottom: 1px solid var(--border); padding-bottom: 24px; margin-bottom: 32px; }\n");
        html.append(".tagline { color: var(--sand); font-size: 14px; font-weight: 500; text-transform: uppercase; letter-spacing: 0.08em; margin-bottom: 6px; }\n");
        html.append(".title { font-size: 26px; font-weight: 700; }\n");
        html.append(".target { color: var(--muted); font-family: 'JetBrains Mono', monospace; font-size: 13px; margin-top: 4px; }\n");
        html.append(".stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 32px; }\n");
        html.append(".stat-card { background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 18px; }\n");
        html.append(".stat-label { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: 0.05em; margin-bottom: 4px; }\n");
        html.append(".stat-value { font-size: 24px; font-weight: 700; font-family: 'JetBrains Mono', monospace; }\n");
        html.append(".stat-alive { color: var(--red); }\n");
        html.append(".stat-buried { color: var(--muted); }\n");
        html.append(".panel { background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 24px; margin-bottom: 32px; }\n");
        html.append(".panel-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; color: var(--sand); }\n");
        html.append("table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }\n");
        html.append("th { border-bottom: 1px solid var(--border); padding: 10px 12px; color: var(--muted); font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; }\n");
        html.append("td { border-bottom: 1px solid var(--border); padding: 12px; vertical-align: top; font-family: 'JetBrains Mono', monospace; font-size: 12px; }\n");
        html.append(".badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; text-transform: uppercase; }\n");
        html.append(".badge-critical { background: rgba(248,81,73,0.15); color: var(--red); border: 1px solid var(--red); }\n");
        html.append(".badge-high { background: rgba(210,153,34,0.15); color: var(--amber); border: 1px solid var(--amber); }\n");
        html.append(".badge-medium { background: rgba(88,166,255,0.15); color: var(--blue); border: 1px solid var(--blue); }\n");
        html.append(".badge-low { background: rgba(139,148,158,0.15); color: var(--muted); border: 1px solid var(--muted); }\n");
        html.append(".tag-alive { color: var(--red); font-weight: 700; }\n");
        html.append(".tag-buried { color: var(--muted); }\n");
        html.append(".snippet { color: var(--muted); background: #0e1012; padding: 4px 8px; border-radius: 4px; margin-top: 4px; word-break: break-all; }\n");
        html.append(".remediation-box { background: #0e1012; border-left: 3px solid var(--sand); padding: 12px; margin-top: 16px; font-size: 13px; }\n");
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<header>\n");
        html.append("<div class=\"tagline\">gravedig &bull; Secrets Scanner</div>\n");
        html.append("<h1 class=\"title\">\"Dig up the secrets your git history forgot to bury.\"</h1>\n");
        html.append("<div class=\"target\">Target: ").append(escape(target)).append("</div>\n");
        html.append("</header>\n");

        // Stats cards
        html.append("<div class=\"stats\">\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">Total Findings</div><div class=\"stat-value\">").append(s.getTotalFindings()).append("</div></div>\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">ALIVE (In HEAD)</div><div class=\"stat-value stat-alive\">").append(s.getAliveFindings()).append("</div></div>\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">BURIED (In History)</div><div class=\"stat-value stat-buried\">").append(s.getBuriedFindings()).append("</div></div>\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">Files Scanned</div><div class=\"stat-value\">").append(s.getFilesScanned()).append("</div></div>\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">Commits Scanned</div><div class=\"stat-value\">").append(s.getCommitsScanned()).append("</div></div>\n");
        html.append("<div class=\"stat-card\"><div class=\"stat-label\">Scan Duration</div><div class=\"stat-value\">").append(s.getDurationMs()).append("ms</div></div>\n");
        html.append("</div>\n");

        // Findings table
        html.append("<div class=\"panel\">\n");
        html.append("<div class=\"panel-title\">Discovered Secrets Ranked by Risk</div>\n");
        html.append("<table><thead><tr><th>Severity</th><th>Risk</th><th>Rule</th><th>Location</th><th>Secret</th><th>Commit</th><th>Exposure</th><th>Presence</th></tr></thead>\n<tbody>\n");

        for (Finding f : findings) {
            String badgeClass = "badge-" + (f.getSeverity() != null ? f.getSeverity().name().toLowerCase() : "medium");
            String presenceClass = f.getPresence() == FindingPresence.ALIVE ? "tag-alive" : "tag-buried";
            String exposure = f.getExposureWindow() != null ? f.getExposureWindow().getDurationFormatted() : "-";
            String commit = f.getCommitHash() != null && !f.getCommitHash().isEmpty() ? f.getCommitHash() : "HEAD";

            html.append("<tr>\n");
            html.append("<td><span class=\"badge ").append(badgeClass).append("\">").append(f.getSeverity()).append("</span></td>\n");
            html.append("<td><strong>").append(f.getRiskScore()).append("</strong>/100</td>\n");
            html.append("<td>").append(escape(f.getRuleId())).append("</td>\n");
            html.append("<td>").append(escape(f.getFilePath())).append(":").append(f.getLineNumber()).append("</td>\n");
            html.append("<td><div>").append(escape(f.getSecretRedacted())).append("</div><div class=\"snippet\">").append(escape(f.getSnippetRedacted())).append("</div></td>\n");
            html.append("<td>").append(escape(commit)).append("</td>\n");
            html.append("<td>").append(escape(exposure)).append("</td>\n");
            html.append("<td><span class=\"").append(presenceClass).append("\">").append(f.getPresence()).append("</span></td>\n");
            html.append("</tr>\n");
        }

        html.append("</tbody></table>\n");
        html.append("</div>\n");

        // Remediation instructions
        html.append("<div class=\"panel\">\n");
        html.append("<div class=\"panel-title\">Remediation & History Cleanup Guide</div>\n");
        html.append("<p style=\"font-size: 13px; color: var(--muted);\">Follow these steps strictly in order: <strong>Rotate the secret first</strong> with the provider, then rewrite git history.</p>\n");
        html.append("<div class=\"remediation-box\">\n");
        html.append("<strong>Step 1: Revoke & Rotate Credentials</strong><br/>Revoke live credentials with provider immediately.<br/><br/>\n");
        html.append("<strong>Step 2: Remove from Git History with git filter-repo</strong><br/>\n");
        html.append("<code style=\"color: var(--sand);\">pip install git-filter-repo && git filter-repo --invert-paths --path &lt;file-to-purge&gt;</code>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        html.append("</body></html>\n");
        return html.toString();
    }

    public static void print(ScanResult result, PrintStream out) {
        out.println(format(result));
    }

    private static String escape(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
