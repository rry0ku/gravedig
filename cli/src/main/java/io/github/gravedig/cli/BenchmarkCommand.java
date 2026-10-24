package io.github.gravedig.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.gravedig.core.benchmark.BenchmarkResult;
import io.github.gravedig.core.benchmark.BenchmarkRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "benchmark",
        description = "Run high-throughput scanning and entropy performance benchmarks.",
        mixinStandardHelpOptions = true
)
public class BenchmarkCommand implements Callable<Integer> {

    @Option(names = {"-i", "--iterations"}, description = "Number of iterations for microbenchmarks (default: 50,000)", defaultValue = "50000")
    private int iterations;

    @Option(names = {"-c", "--commits"}, description = "Number of commits for synthetic git history benchmark (default: 100)", defaultValue = "100")
    private int commits;

    @Option(names = {"--json"}, description = "Output results in JSON format")
    private boolean jsonOutput;

    @Override
    public Integer call() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkResult> results = new ArrayList<>();

        if (!jsonOutput) {
            System.out.println("  ____                                 _ _       ");
            System.out.println(" / ___|_ __ __ ___   _____  __| (_) __ _ ");
            System.out.println("| |  _| '__/ _` \\ \\ / / _ \\/ _` | |/ _` |");
            System.out.println("| |_| | | | (_| |\\ V /  __/ (_| | | (_| |");
            System.out.println(" \\____|_|  \\__,_| \\_/ \\___|\\__,_|_|\\__, |");
            System.out.println("                                   |___/ ");
            System.out.println(" \"Dig up the secrets your git history forgot to bury.\"\n");
            System.out.println("Running Gravedig Performance Benchmarks...");
            System.out.println("------------------------------------------------------------");
        }

        // 1. Shannon Entropy Benchmark
        BenchmarkResult entropyResult = runner.benchmarkEntropy(iterations);
        results.add(entropyResult);
        if (!jsonOutput) {
            System.out.println(entropyResult.toFormattedString());
        }

        // 2. Rule Engine Regex Benchmark
        BenchmarkResult ruleResult = runner.benchmarkRuleEngine(iterations);
        results.add(ruleResult);
        if (!jsonOutput) {
            System.out.println(ruleResult.toFormattedString());
        }

        // 3. JGit History Excavation Benchmark
        BenchmarkResult gitResult = runner.benchmarkGitHistory(commits);
        results.add(gitResult);
        if (!jsonOutput) {
            System.out.println(gitResult.toFormattedString());
            System.out.println("------------------------------------------------------------");
            System.out.println("✓ All benchmarks completed successfully.\n");
        } else {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(results));
        }

        return 0;
    }
}
