package io.github.gravedig.cli;

import io.github.gravedig.cli.formatter.HtmlReportFormatter;
import io.github.gravedig.cli.formatter.JsonOutputFormatter;
import io.github.gravedig.cli.formatter.SarifOutputFormatter;
import io.github.gravedig.cli.formatter.TableOutputFormatter;
import io.github.gravedig.core.baseline.BaselineManager;
import io.github.gravedig.core.config.ConfigLoader;
import io.github.gravedig.core.config.GravedigConfig;
import io.github.gravedig.core.git.GitHistoryScanner;
import io.github.gravedig.core.git.MultiRepoScanner;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.rule.RuleLoader;
import io.github.gravedig.core.scan.DirectoryScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "scan",
        description = "Scan directory or repository for leaked credentials",
        mixinStandardHelpOptions = true
)
public class ScanCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "Target path to scan (directory or file)",
            defaultValue = "."
    )
    private String pathStr;

    @Option(
            names = {"--history"},
            description = "Scan full git history (all branches, commits, tags)"
    )
    private boolean history;

    @Option(
            names = {"--threads"},
            description = "Number of worker threads (default: available CPU cores)",
            defaultValue = "0"
    )
    private int threads;

    @Option(
            names = {"--format"},
            description = "Output format: table, json, sarif, html (default: ${DEFAULT-VALUE})",
            defaultValue = "table"
    )
    private String format;

    @Option(
            names = {"--output", "-o"},
            description = "Path to write output file (defaults to stdout)"
    )
    private String outputPath;

    @Option(
            names = {"--baseline"},
            description = "Path to baseline findings file"
    )
    private String baselinePath;

    @Option(
            names = {"--generate-baseline"},
            description = "Generate a baseline file from current findings and exit"
    )
    private String generateBaselinePath;

    @Option(
            names = {"--fail-on"},
            description = "Minimum severity to trigger exit code 1: low, medium, high, critical (default: ${DEFAULT-VALUE})",
            defaultValue = "low"
    )
    private String failOn;

    @Override
    public Integer call() {
        Path target = Paths.get(pathStr).toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            System.err.println("Error: Target path does not exist: " + target);
            return 2;
        }

        Severity failSeverity;
        try {
            failSeverity = Severity.fromString(failOn);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid --fail-on severity '" + failOn + "'. Allowed: low, medium, high, critical.");
            return 2;
        }

        try {
            RuleLoader ruleLoader = new RuleLoader();
            List<Rule> rules = ruleLoader.loadDefaultRules();

            ConfigLoader configLoader = new ConfigLoader();
            GravedigConfig config = configLoader.findAndLoad(target);

            BaselineManager baselineManager = new BaselineManager();
            if (baselinePath != null && !baselinePath.isBlank()) {
                Path bp = Paths.get(baselinePath).toAbsolutePath().normalize();
                baselineManager = BaselineManager.load(bp);
            }

            ScanResult result;

            if (history) {
                Path gitFolder = target.resolve(".git");
                if (Files.exists(gitFolder) || (Files.exists(target.resolve("HEAD")) && Files.exists(target.resolve("objects")))) {
                    GitHistoryScanner gitScanner = new GitHistoryScanner(rules, config, baselineManager, threads);
                    result = gitScanner.scanRepository(target);
                } else {
                    List<Path> subRepos = MultiRepoScanner.discoverGitRepositories(target, 3);
                    if (!subRepos.isEmpty()) {
                        MultiRepoScanner multiScanner = new MultiRepoScanner(rules, config, baselineManager, threads);
                        result = multiScanner.scanAll(target);
                    } else {
                        System.err.println("Warning: Target is not a git repository. Falling back to working directory scan.");
                        DirectoryScanner scanner = new DirectoryScanner(rules, config, baselineManager);
                        result = scanner.scan(target);
                    }
                }
            } else {
                DirectoryScanner scanner = new DirectoryScanner(rules, config, baselineManager);
                result = scanner.scan(target);
            }

            if (generateBaselinePath != null && !generateBaselinePath.isBlank()) {
                Path bp = Paths.get(generateBaselinePath).toAbsolutePath().normalize();
                BaselineManager.save(bp, result.getFindings());
                System.out.println("Generated baseline file with " + result.getFindings().size() + " findings at: " + bp);
                return 0;
            }

            PrintStream out = System.out;
            FileOutputStream fos = null;
            if (outputPath != null && !outputPath.isBlank()) {
                Path outPath = Paths.get(outputPath).toAbsolutePath().normalize();
                if (outPath.getParent() != null && !Files.exists(outPath.getParent())) {
                    Files.createDirectories(outPath.getParent());
                }
                fos = new FileOutputStream(outPath.toFile());
                out = new PrintStream(fos);
            }

            try {
                if ("json".equalsIgnoreCase(format)) {
                    JsonOutputFormatter.print(result, out);
                } else if ("sarif".equalsIgnoreCase(format)) {
                    SarifOutputFormatter.print(result, out);
                } else if ("html".equalsIgnoreCase(format)) {
                    HtmlReportFormatter.print(result, out);
                } else {
                    TableOutputFormatter.print(result, out);
                }
            } finally {
                if (fos != null) {
                    out.flush();
                    fos.close();
                    System.out.println("Scan report written to: " + outputPath);
                }
            }

            // Determine exit code (ignoring ACCEPTED_RISK and FALSE_POSITIVE)
            boolean hasFailingSeverity = result.getFindings().stream()
                    .filter(f -> f.getStatus() != FindingStatus.ACCEPTED_RISK && f.getStatus() != FindingStatus.FALSE_POSITIVE)
                    .anyMatch(f -> f.getSeverity().isAtLeast(failSeverity));

            return hasFailingSeverity ? 1 : 0;
        } catch (Exception e) {
            System.err.println("Scan execution error: " + e.getMessage());
            return 2;
        }
    }
}
