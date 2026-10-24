package io.github.gravedig.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String CONFIG_FILENAME_YML = ".gravedig.yml";
    private static final String CONFIG_FILENAME_YAML = ".gravedig.yaml";

    private final ObjectMapper yamlMapper;

    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public GravedigConfig findAndLoad(Path searchDir) {
        if (searchDir == null || !Files.exists(searchDir)) {
            return GravedigConfig.defaultConfiguration();
        }

        Path targetDir = Files.isDirectory(searchDir) ? searchDir : searchDir.getParent();
        if (targetDir == null) {
            return GravedigConfig.defaultConfiguration();
        }

        Path ymlPath = targetDir.resolve(CONFIG_FILENAME_YML);
        if (Files.exists(ymlPath) && Files.isRegularFile(ymlPath)) {
            return loadFromFile(ymlPath);
        }

        Path yamlPath = targetDir.resolve(CONFIG_FILENAME_YAML);
        if (Files.exists(yamlPath) && Files.isRegularFile(yamlPath)) {
            return loadFromFile(yamlPath);
        }

        return GravedigConfig.defaultConfiguration();
    }

    public GravedigConfig loadFromFile(Path path) {
        try (InputStream in = new FileInputStream(path.toFile())) {
            GravedigConfig cfg = yamlMapper.readValue(in, GravedigConfig.class);
            return cfg != null ? cfg : GravedigConfig.defaultConfiguration();
        } catch (Exception e) {
            log.warn("Failed to load config file at {}: {}. Falling back to default configuration.", path, e.getMessage());
            return GravedigConfig.defaultConfiguration();
        }
    }
}
