package com.w6w.migrate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateCaseType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes a reviewable migration draft. Dry-run returns the same target list without touching disk. */
public class MigrationFileWriter {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory()).enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public Map<String, String> write(BlueprintMigrationService.CaseMigration migration,
                                     IntermediateCaseType sourceCase,
                                     Path targetDirectory,
                                     boolean dryRun) throws IOException {
        Map<String, String> planned = new LinkedHashMap<>();
        planned.put("migration-report.md", migration.report().toMarkdown());
        if (migration.eligibility().getDecision().name().equals("REJECT")) {
            if (!dryRun) {
                Files.createDirectories(targetDirectory);
                Files.writeString(targetDirectory.resolve("migration-report.md"), planned.get("migration-report.md"));
            }
            return planned;
        }

        planned.put("workflow.cwm.yaml", yaml.writeValueAsString(migration.draft()));
        planned.put("schemas/data.schema.json", json.writeValueAsString(dataSchema(sourceCase)));
        planned.put("tests/scenarios/migration-smoke.json", json.writeValueAsString(smokeScenario(migration.draft().getMetadata().getKey())));
        for (var stage : migration.draft().getStages()) {
            for (var step : stage.getSteps()) {
                if ("userTask".equals(step.getType())) {
                    planned.put(step.getForm(), json.writeValueAsString(dataSchema(sourceCase)));
                }
            }
        }
        if (!dryRun) {
            for (var entry : planned.entrySet()) {
                Path destination = targetDirectory.resolve(entry.getKey()).normalize();
                if (!destination.startsWith(targetDirectory.normalize())) throw new IOException("Migration output path escaped target directory");
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, entry.getValue());
            }
        }
        return planned;
    }

    private Map<String, Object> dataSchema(IntermediateCaseType sourceCase) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var field : sourceCase.getDataFields()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", jsonType(field.getType()));
            if (field.getDescription() != null && !field.getDescription().isBlank()) property.put("description", field.getDescription());
            properties.put(field.getName(), property);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("title", sourceCase.getName() + " data");
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private String jsonType(String sourceType) {
        if (sourceType == null) return "string";
        return switch (sourceType.toLowerCase()) {
            case "integer" -> "integer";
            case "decimal", "currency" -> "number";
            case "boolean" -> "boolean";
            case "date" -> "string";
            default -> "string";
        };
    }

    private Map<String, Object> smokeScenario(String key) {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("workflow", key);
        scenario.put("mode", "manual-review-required");
        scenario.put("description", "Complete generated user tasks with simulated users after role bindings and service actions are implemented.");
        return scenario;
    }
}
