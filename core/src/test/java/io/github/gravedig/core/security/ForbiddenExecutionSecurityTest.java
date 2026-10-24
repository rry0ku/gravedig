package io.github.gravedig.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenExecutionSecurityTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "ProcessBuilder",
            "Runtime.getRuntime().exec",
            "Runtime.exec",
            "ProcessHandle",
            "java.lang.Process"
    );

    @Test
    @DisplayName("Guarantees that no external processes or command executions exist anywhere in source code")
    void verifyNoProcessExecutionInSourceCode() throws IOException {
        // Resolve project root by finding parent directory containing pom.xml
        Path currentDir = Paths.get("").toAbsolutePath();
        Path repoRoot = currentDir;
        if (currentDir.endsWith("core") || currentDir.endsWith("cli") || currentDir.endsWith("server")) {
            repoRoot = currentDir.getParent();
        }

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 // Exclude this test file itself so checking for forbidden tokens doesn't trigger a false alarm
                 .filter(p -> !p.getFileName().toString().equals("ForbiddenExecutionSecurityTest.java"))
                 .forEach(file -> {
                     try {
                         List<String> lines = Files.readAllLines(file);
                         for (int i = 0; i < lines.size(); i++) {
                             String line = lines.get(i);
                             for (String token : FORBIDDEN_TOKENS) {
                                 if (line.contains(token)) {
                                     violations.add(String.format("Forbidden call '%s' at %s:%d -> %s",
                                             token, file.toAbsolutePath(), i + 1, line.trim()));
                                 }
                             }
                         }
                     } catch (IOException e) {
                         throw new RuntimeException("Failed reading java file: " + file, e);
                     }
                 });
        }

        assertThat(violations)
                .as("gravedig must never invoke external processes or execute code from scanned repos")
                .isEmpty();
    }
}
