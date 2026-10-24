package io.github.gravedig.core.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkRunnerTest {

    @Test
    @DisplayName("Entropy benchmark runs and calculates throughput")
    void testEntropyBenchmark() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkResult result = runner.benchmarkEntropy(1000);

        assertThat(result.operations()).isEqualTo(1000);
        assertThat(result.opsPerSecond()).isGreaterThan(0.0);
        assertThat(result.toFormattedString()).contains("Shannon Entropy Calculator");
    }

    @Test
    @DisplayName("Rule engine benchmark evaluates lines across all rules")
    void testRuleEngineBenchmark() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkResult result = runner.benchmarkRuleEngine(500);

        assertThat(result.operations()).isEqualTo(500);
        assertThat(result.opsPerSecond()).isGreaterThan(0.0);
        assertThat(result.metrics().get("rulesEvaluatedPerLine")).isEqualTo(15);
    }

    @Test
    @DisplayName("Synthetic Git repository benchmark excavates commits")
    void testGitHistoryBenchmark() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkResult result = runner.benchmarkGitHistory(10);

        assertThat(result.operations()).isEqualTo(10);
        assertThat(result.opsPerSecond()).isGreaterThan(0.0);
        assertThat(result.toFormattedString()).contains("JGit History Excavation");
    }
}
