package io.github.gravedig.core.filter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FalsePositiveFilter {

    private static final Set<String> DEFAULT_PLACEHOLDER_SUBSTRINGS = Set.of(
            "your_api_key",
            "your-api-key",
            "yourapikey",
            "your_token",
            "your-token",
            "your_secret",
            "<api_key>",
            "[api_key]",
            "example",
            "xxxx",
            "changeme",
            "dummy",
            "placeholder",
            "replace_me",
            "my_secret",
            "00000000",
            "12345678",
            "sample_key"
    );

    private static final Set<String> LOCKFILES = Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "cargo.lock",
            "gemfile.lock",
            "composer.lock",
            "poetry.lock",
            "go.sum",
            "mix.lock",
            "pubspec.lock",
            "gradle.lockfile"
    );

    private static final Set<String> IGNORED_PATH_PARTS = Set.of(
            "node_modules",
            "vendor",
            ".venv",
            "venv",
            "target",
            "build",
            "dist",
            ".gradle",
            ".idea",
            ".vscode"
    );

    private final Set<String> customPlaceholders;
    private final Set<String> customIgnoredPaths;

    public FalsePositiveFilter() {
        this(Set.of(), Set.of());
    }

    public FalsePositiveFilter(Collection<String> customPlaceholders, Collection<String> customIgnoredPaths) {
        this.customPlaceholders = new HashSet<>(DEFAULT_PLACEHOLDER_SUBSTRINGS);
        if (customPlaceholders != null) {
            for (String p : customPlaceholders) {
                if (p != null && !p.isBlank()) {
                    this.customPlaceholders.add(p.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.customIgnoredPaths = new HashSet<>(IGNORED_PATH_PARTS);
        if (customIgnoredPaths != null) {
            for (String p : customIgnoredPaths) {
                if (p != null && !p.isBlank()) {
                    this.customIgnoredPaths.add(p.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public boolean isPlaceholder(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return true;
        }

        String lower = rawSecret.toLowerCase(Locale.ROOT);
        for (String ph : customPlaceholders) {
            if (lower.contains(ph)) {
                return true;
            }
        }

        if (isTriviallyRepetitive(rawSecret)) {
            return true;
        }

        return false;
    }

    public boolean isIgnoredFile(Path file) {
        if (file == null) {
            return true;
        }
        String fileName = file.getFileName() != null ? file.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if (LOCKFILES.contains(fileName)) {
            return true;
        }

        String normalized = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String part : customIgnoredPaths) {
            if (normalized.contains("/" + part + "/") || normalized.endsWith("/" + part) || normalized.startsWith(part + "/")) {
                return true;
            }
        }

        return false;
    }

    private boolean isTriviallyRepetitive(String s) {
        if (s.length() < 6) return false;
        char first = s.charAt(0);
        boolean allSame = true;
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                allSame = false;
                break;
            }
        }
        return allSame;
    }
}
