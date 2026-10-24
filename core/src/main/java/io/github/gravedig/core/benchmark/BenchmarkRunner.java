package io.github.gravedig.core.benchmark;

import io.github.gravedig.core.entropy.EntropyCalculator;
import io.github.gravedig.core.git.GitHistoryScanner;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.rule.RuleLoader;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    static {
        System.setProperty("jgit.fs.filestoreattributes.save", "false");
    }

    private static final List<String> SAMPLE_LINES = List.of(
            "const apiKey = 'AKIAIOSFODNN7EXAMPLE';",
            "import React from 'react';",
            "export const SLACK_TOKEN = 'xox" + "b-123456789012-1234567890123-abcdefghijklmnopqrstuvwx';",
            "function calculateTotal(items) { return items.reduce((acc, i) => acc + i.price, 0); }",
            "const gitHubPat = 'ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';",
            "// This is a normal comment describing the authentication workflow",
            "const dbUrl = 'postgres://admin:SuperSecretPassword123@db.internal:5432/production';",
            "for (let i = 0; i < 100; i++) { console.log('Index: ' + i); }",
            "const stripeKey = 'sk" + "_live_51Abcd1234Efgh5678Ijkl9012Mnop3456Qrst';",
            "const genericPassword = 'password = \"SuperSecretDevPassword2024!\";'"
    );

    public BenchmarkResult benchmarkEntropy(int iterations) {
        // Warmup
        for (int i = 0; i < 1000; i++) {
            EntropyCalculator.calculate(SAMPLE_LINES.get(i % SAMPLE_LINES.size()));
        }

        long totalBytes = 0;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String sample = SAMPLE_LINES.get(i % SAMPLE_LINES.size());
            totalBytes += sample.length();
            EntropyCalculator.calculate(sample);
        }
        long elapsedNanos = System.nanoTime() - start;
        long durationMs = Math.max(1, elapsedNanos / 1_000_000);

        double opsPerSec = (double) iterations / (elapsedNanos / 1_000_000_000.0);
        double mbPerSec = ((double) totalBytes / (1024 * 1024)) / (elapsedNanos / 1_000_000_000.0);

        return new BenchmarkResult(
                "Shannon Entropy Calculator",
                iterations,
                durationMs,
                opsPerSec,
                mbPerSec,
                Map.of("avgNanosPerOp", String.format("%.1f ns", (double) elapsedNanos / iterations))
        );
    }

    public BenchmarkResult benchmarkRuleEngine(int iterations) {
        List<Rule> rules = new RuleLoader().loadDefaultRules();

        // Warmup
        for (int i = 0; i < 500; i++) {
            String sample = SAMPLE_LINES.get(i % SAMPLE_LINES.size());
            for (Rule rule : rules) {
                if (rule.matchesFastCheck(sample)) {
                    rule.getCompiledPattern().matcher(sample).find();
                }
            }
        }

        long totalBytes = 0;
        int matchesFound = 0;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String sample = SAMPLE_LINES.get(i % SAMPLE_LINES.size());
            totalBytes += sample.length();
            for (Rule rule : rules) {
                if (rule.matchesFastCheck(sample)) {
                    Matcher m = rule.getCompiledPattern().matcher(sample);
                    if (m.find()) {
                        matchesFound++;
                    }
                }
            }
        }
        long elapsedNanos = System.nanoTime() - start;
        long durationMs = Math.max(1, elapsedNanos / 1_000_000);

        double opsPerSec = (double) iterations / (elapsedNanos / 1_000_000_000.0);
        double mbPerSec = ((double) totalBytes / (1024 * 1024)) / (elapsedNanos / 1_000_000_000.0);

        return new BenchmarkResult(
                "Rule Engine Regex Evaluation (" + rules.size() + " active rules)",
                iterations,
                durationMs,
                opsPerSec,
                mbPerSec,
                Map.of(
                        "rulesEvaluatedPerLine", rules.size(),
                        "totalMatchesFound", matchesFound,
                        "avgLatencyPerLine", String.format("%.2f µs", (double) elapsedNanos / iterations / 1000.0)
                )
        );
    }

    public BenchmarkResult benchmarkGitHistory(int commitCount) throws Exception {
        Path tempRepoDir = Files.createTempDirectory("gravedig-bench-repo-");
        try {
            // Build synthetic git repository using pure JGit
            try (Git git = Git.init().setDirectory(tempRepoDir.toFile()).call()) {
                for (int i = 0; i < commitCount; i++) {
                    String fileName = "src/module_" + (i % 5) + ".js";
                    File file = tempRepoDir.resolve(fileName).toFile();
                    file.getParentFile().mkdirs();

                    String content = SAMPLE_LINES.get(i % SAMPLE_LINES.size()) + "\n// Commit " + i;
                    Files.writeString(file.toPath(), content);

                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Commit " + i + ": update " + fileName).call();
                }
            }

            List<Rule> rules = new RuleLoader().loadDefaultRules();
            GitHistoryScanner scanner = new GitHistoryScanner(rules, null, null, 1);

            long start = System.nanoTime();
            ScanResult result = scanner.scanRepository(tempRepoDir);
            long elapsedNanos = System.nanoTime() - start;
            long durationMs = Math.max(1, elapsedNanos / 1_000_000);

            double commitsPerSec = (double) commitCount / (elapsedNanos / 1_000_000_000.0);
            long findingsCount = result.getFindings().size();

            return new BenchmarkResult(
                    "JGit History Excavation & AST Diff Parsing",
                    commitCount,
                    durationMs,
                    commitsPerSec,
                    0.0,
                    Map.of(
                            "commitsScanned", commitCount,
                            "findingsExcavated", findingsCount,
                            "commitsPerSecond", String.format("%,.0f commits/s", commitsPerSec)
                    )
            );
        } finally {
            deleteDirectoryRecursively(tempRepoDir.toFile());
        }
    }

    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectoryRecursively(child);
                }
            }
        }
        file.delete();
    }
}
