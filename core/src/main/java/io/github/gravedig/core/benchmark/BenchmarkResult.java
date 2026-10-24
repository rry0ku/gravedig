package io.github.gravedig.core.benchmark;

import java.util.Map;

public record BenchmarkResult(
        String name,
        long operations,
        long durationMs,
        double opsPerSecond,
        double throughputMbPerSec,
        Map<String, Object> metrics
) {
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  [%s]\n", name));
        sb.append(String.format("    Operations: %s\n", String.format("%,d", operations)));
        sb.append(String.format("    Duration:   %d ms\n", durationMs));
        sb.append(String.format("    Throughput: %,.0f ops/sec (%.2f MB/s)\n", opsPerSecond, throughputMbPerSec));
        if (metrics != null && !metrics.isEmpty()) {
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                sb.append(String.format("    %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        return sb.toString();
    }
}
