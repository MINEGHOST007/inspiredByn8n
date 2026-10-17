package com.w6w.migrate.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Versioned, overridable source-step mapping rules for Blueprint adapters. */
public final class BlueprintMappingRules {
    private static final String DEFAULT_RESOURCE = "/migration/blueprint-mappings-v1.yaml";
    private final int version;
    private final Map<String, Rule> stepMappings;

    private BlueprintMappingRules(int version, Map<String, Rule> stepMappings) {
        if (version != 1) throw new IllegalArgumentException("Unsupported Blueprint mapping rules version: " + version);
        if (stepMappings == null || stepMappings.isEmpty()) throw new IllegalArgumentException("At least one step mapping is required");
        this.version = version;
        this.stepMappings = new LinkedHashMap<>();
        stepMappings.forEach((source, rule) -> {
            if (source == null || source.isBlank() || rule == null || rule.strategy() == null || rule.targetType() == null)
                throw new IllegalArgumentException("Every step mapping needs a source, strategy, and targetType");
            this.stepMappings.put(source.toLowerCase(Locale.ROOT), rule);
        });
    }

    public static BlueprintMappingRules defaults() {
        try (InputStream input = BlueprintMappingRules.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing packaged mapping rules " + DEFAULT_RESOURCE);
            return read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged Blueprint mapping rules", e);
        }
    }

    public static BlueprintMappingRules fromFile(Path path) throws IOException {
        try (InputStream input = java.nio.file.Files.newInputStream(path)) { return read(input); }
    }

    private static BlueprintMappingRules read(InputStream input) throws IOException {
        RuleFile config = new ObjectMapper(new YAMLFactory()).readValue(input, RuleFile.class);
        return new BlueprintMappingRules(config.version, config.stepMappings);
    }

    public Rule forSourceType(String sourceType) {
        return stepMappings.getOrDefault(sourceType == null ? "unknown" : sourceType.toLowerCase(Locale.ROOT),
                new Rule("UNSUPPORTED", "unsupported", "low", "No declarative mapping rule exists for this source type."));
    }

    public int version() { return version; }

    public record Rule(String strategy, String targetType, String confidence, String reason) { }

    public static final class RuleFile {
        public int version;
        public Map<String, Rule> stepMappings = new LinkedHashMap<>();
    }
}
