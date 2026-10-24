package io.github.gravedig.core.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.gravedig.core.model.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuleLoader {
    private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);
    private static final String DEFAULT_RULES_PATH = "/rules/default-rules.yml";

    private final ObjectMapper yamlMapper;

    public RuleLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<Rule> loadDefaultRules() {
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_RULES_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Default rules file not found on classpath: " + DEFAULT_RULES_PATH);
            }
            return loadFromStream(in);
        } catch (IOException e) {
            log.error("Failed to load default rules from classpath", e);
            throw new RuntimeException("Could not load default rules", e);
        }
    }

    public List<Rule> loadFromFile(Path path) throws IOException {
        try (InputStream in = new FileInputStream(path.toFile())) {
            return loadFromStream(in);
        }
    }

    public List<Rule> loadFromFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return loadFromStream(in);
        }
    }

    public List<Rule> loadFromStream(InputStream inputStream) throws IOException {
        JsonNode root = yamlMapper.readTree(inputStream);
        if (root == null) {
            return Collections.emptyList();
        }

        JsonNode rulesNode = root.has("rules") ? root.get("rules") : root;
        if (!rulesNode.isArray()) {
            return Collections.emptyList();
        }

        List<Rule> rules = new ArrayList<>();
        for (JsonNode ruleNode : rulesNode) {
            try {
                Rule rule = yamlMapper.treeToValue(ruleNode, Rule.class);
                rules.add(rule);
            } catch (Exception e) {
                log.warn("Skipping invalid rule in configuration: {}", ruleNode, e);
            }
        }
        return Collections.unmodifiableList(rules);
    }
}
