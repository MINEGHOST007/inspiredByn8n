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
        planned.put("tests/scenarios/migration-smoke.yaml", yaml.writeValueAsString(smokeScenario(migration.draft())));
        for (var stage : migration.draft().getStages()) {
            for (var step : stage.getSteps()) {
                if ("userTask".equals(step.getType()) || "approval".equals(step.getType())) {
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

    private Map<String, Object> smokeScenario(com.w6w.model.WorkflowDefinition workflow) {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", "Generated migration smoke scenario");
        scenario.put("workflowKey", workflow.getMetadata().getKey());
        scenario.put("initialData", Map.of());
        java.util.List<Map<String, Object>> steps = new java.util.ArrayList<>();
        steps.add(Map.of("action", "startWorkflow"));
        for (var stage : workflow.getStages()) for (var step : stage.getSteps()) {
            if (!"userTask".equals(step.getType()) && !"approval".equals(step.getType())) continue;
            Map<String, Object> complete = new LinkedHashMap<>();
            complete.put("action", "approval".equals(step.getType()) ? "completeApproval" : "completeTask");
            complete.put("stepId", step.getId());
            complete.put("user", "migration.reviewer");
            complete.put("formData", Map.of());
            if (step.getOutcomes() != null && !step.getOutcomes().isEmpty())
                complete.put("outcome", step.getOutcomes().keySet().iterator().next());
            steps.add(complete);
        }
        scenario.put("steps", steps);
        Map<String, Object> assertions = new LinkedHashMap<>();
        for (int i = workflow.getStages().size() - 1; i >= 0; i--) {
            var stage = workflow.getStages().get(i);
            if (!stage.isAlternate() && stage.getResolution() != null && !stage.getResolution().isBlank()) {
                assertions.put("finalResolution", stage.getResolution());
                break;
            }
        }
        scenario.put("assertions", assertions);
        return scenario;
    }
}
