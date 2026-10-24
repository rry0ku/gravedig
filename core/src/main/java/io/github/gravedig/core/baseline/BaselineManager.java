package io.github.gravedig.core.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.gravedig.core.model.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BaselineManager {
    private static final Logger log = LoggerFactory.getLogger(BaselineManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Set<BaselineEntry> entries;

    public BaselineManager() {
        this.entries = new HashSet<>();
    }

    public BaselineManager(Set<BaselineEntry> entries) {
        this.entries = entries != null ? new HashSet<>(entries) : new HashSet<>();
    }

    public static BaselineManager load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return new BaselineManager();
        }
        List<BaselineEntry> list = MAPPER.readValue(path.toFile(), new TypeReference<List<BaselineEntry>>() {});
        return new BaselineManager(new HashSet<>(list));
    }

    public static void save(Path path, List<Finding> findings) throws IOException {
        Set<BaselineEntry> entries = findings.stream()
                .map(f -> new BaselineEntry(f.getFingerprint(), f.getRuleId(), f.getFilePath()))
                .collect(Collectors.toSet());
        MAPPER.writeValue(path.toFile(), entries);
    }

    public boolean isBaselined(Finding finding) {
        if (finding == null || entries.isEmpty()) {
            return false;
        }
        BaselineEntry candidate = new BaselineEntry(
                finding.getFingerprint(),
                finding.getRuleId(),
                finding.getFilePath()
        );
        return entries.contains(candidate);
    }

    public Set<BaselineEntry> getEntries() {
        return Collections.unmodifiableSet(entries);
    }
}
