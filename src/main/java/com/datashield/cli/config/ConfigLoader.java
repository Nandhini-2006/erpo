package com.datashield.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private ConfigLoader() {
    }

    public static CliConfig load(String pathString) throws Exception {
        Path path = null;
        if (pathString != null && !pathString.isBlank()) {
            path = Paths.get(pathString);
        } else {
            // Check default locations: ./datashield.yaml, ./datashield.json, ~/.datashield/config.yaml
            if (Files.exists(Paths.get("datashield.yaml"))) {
                path = Paths.get("datashield.yaml");
            } else if (Files.exists(Paths.get("datashield.yml"))) {
                path = Paths.get("datashield.yml");
            } else if (Files.exists(Paths.get("datashield.json"))) {
                path = Paths.get("datashield.json");
            } else {
                Path homeConfig = Paths.get(System.getProperty("user.home"), ".datashield", "config.yaml");
                if (Files.exists(homeConfig)) {
                    path = homeConfig;
                }
            }
        }

        if (path == null || !Files.exists(path)) {
            if (pathString != null && !pathString.isBlank()) {
                throw new FileNotFoundException("Config file not found: " + pathString);
            }
            return new CliConfig();
        }

        log.info("Loading configuration from: {}", path.toAbsolutePath());
        if (path.toString().endsWith(".json")) {
            return JSON_MAPPER.readValue(path.toFile(), CliConfig.class);
        } else {
            return YAML_MAPPER.readValue(path.toFile(), CliConfig.class);
        }
    }
}

