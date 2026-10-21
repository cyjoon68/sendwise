package com.sendwise.carriermock.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CodesLoader {

    private static final Logger log = LoggerFactory.getLogger(CodesLoader.class);

    private static final Set<String> CARRIERS = Set.of("SKT", "KT", "LGU");

    private static final List<String> FILE_CANDIDATES = List.of(
            "packages/rules/carrier-codes.json",
            "../packages/rules/carrier-codes.json",
            "../../packages/rules/carrier-codes.json");

    private final ObjectMapper objectMapper;

    public CodesLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Map<String, String>> load(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("carrier codes file not found: " + path.toAbsolutePath());
            }
            return read(path);
        }
        for (String candidate : FILE_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return read(path);
            }
        }
        throw new IllegalStateException("packages/rules/carrier-codes.json was not found");
    }

    public Map<String, Map<String, String>> parse(byte[] bytes) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("carrier codes root must be an object");
            }
            Map<String, Map<String, String>> carriers = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : root.properties()) {
                if (!field.getValue().isObject() || !CARRIERS.contains(field.getKey())) {
                    log.info("skipped carrier-codes key {}", field.getKey());
                    continue;
                }
                Map<String, String> codes = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> code : field.getValue().properties()) {
                    if (!code.getValue().isTextual()) {
                        throw new IllegalStateException(
                                "carrier code " + field.getKey() + " " + code.getKey() + " is not a string");
                    }
                    codes.put(code.getKey(), code.getValue().asText());
                }
                carriers.put(field.getKey(), codes);
            }
            return carriers;
        } catch (IOException ex) {
            throw new IllegalStateException("carrier codes json is unreadable", ex);
        }
    }

    private Map<String, Map<String, String>> read(Path path) {
        try {
            log.info("carrier codes loaded from {}", path.toAbsolutePath());
            return parse(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new IllegalStateException("carrier codes file unreadable: " + path.toAbsolutePath(), ex);
        }
    }
}
