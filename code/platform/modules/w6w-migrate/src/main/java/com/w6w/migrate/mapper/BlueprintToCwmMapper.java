package com.w6w.migrate.mapper;

import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateCaseType;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStage;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStep;
import com.w6w.migrate.report.ConversionReport;
import com.w6w.model.*;

import java.util.ArrayList;
import java.util.Locale;

/** Maps only source facts that can be represented without guessing missing behavior. */
public class BlueprintToCwmMapper {
    private final BlueprintMappingRules rules;

    public BlueprintToCwmMapper() { this(BlueprintMappingRules.defaults()); }

    public BlueprintToCwmMapper(BlueprintMappingRules rules) { this.rules = rules; }

    public WorkflowDefinition mapToWorkflow(IntermediateCaseType caseType, String blueprintId, ConversionReport report) {
        return mapToWorkflow(caseType, blueprintId, report, java.util.List.of());
    }

    public WorkflowDefinition mapToWorkflow(IntermediateCaseType caseType, String blueprintId,
                                            ConversionReport report, java.util.List<String> personas) {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setApiVersion("w6w/v1");
        wf.setKind("Workflow");

        WorkflowMetadata meta = new WorkflowMetadata();
        String key = slug(caseType.getName());
        meta.setKey(key);
        meta.setName(caseType.getName());
        meta.setVersion("1.0.0");
        meta.setTier(1);
        meta.setOwner("migration-review");
        meta.setSource("blueprint:" + blueprintId + ":" + key);
        meta.setDescription(caseType.getDescription());
        // Until the source owner provides a classification, use the strictest
        // supported W6W label so a generated draft cannot silently under-classify data.
        meta.setDataClassification("restricted");
        wf.setMetadata(meta);

        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("schema", "schemas/data.schema.json");
        wf.setData(data);
        java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
        if (personas != null) personas.stream().map(this::slug).filter(role -> !role.isBlank()).forEach(roles::add);
        roles.add("migration_reviewer");
        wf.setRoles(new ArrayList<>(roles));
        report.addActionItem("Replace the provisional owner 'migration-review' with the accountable workflow team.");
        report.addActionItem("Confirm the provisional restricted data classification against the source data policy.");
        report.addActionItem("Map each human task from migration_reviewer to the responsible Blueprint persona and approved W6W role.");
        for (var field : caseType.getDataFields()) {
            report.incrementApproximated();
            report.recordElement("data-field:" + field.getName(), "APPROXIMATED", "medium",
                    "Field name/type extracted from PDF; verify truncation and map business constraints.");
        }
        if (!caseType.getBusinessRules().isEmpty()) {
            for (var rule : caseType.getBusinessRules()) {
                report.incrementUnsupported();
                report.recordElement("business-rule:" + rule.getName(), "UNSUPPORTED", "low",
                        "Rule text was extracted but not converted into executable DMN or CWM logic.");
                report.addActionItem("Author and test decision logic for business rule '" + rule.getName() + "'.");
            }
        }

        IntermediateStage lastPrimaryStage = caseType.getStages().stream().filter(stage -> !stage.isAlternate())
                .reduce((first, second) -> second).orElse(null);
        for (IntermediateStage sourceStage : caseType.getStages()) {
            StageDefinition stage = new StageDefinition();
            stage.setId(slug(sourceStage.getName()));
            stage.setName(sourceStage.getName());
            stage.setAlternate(sourceStage.isAlternate());
            report.incrementConverted();
            report.recordElement("stage:" + stage.getId(), "CONVERTED", "medium", "Stage name/order extracted; verify lifecycle semantics.");
            // Resolution values are omitted unless they are explicitly present in source data.

            for (IntermediateStep sourceStep : sourceStage.getSteps()) {
                StepDefinition step = new StepDefinition();
                step.setId(slug(sourceStep.getName()));
                step.setName(sourceStep.getName());
                BlueprintMappingRules.Rule rule = rules.forSourceType(sourceStep.getStepType());
                step.setType(rule.targetType());

                switch (rule.strategy().toUpperCase(Locale.ROOT)) {
                    case "USER_TASK" -> {
                        step.setForm("forms/" + step.getId() + ".json");
                        step.setAssignee(migrationReviewer());
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Review the generated form and assign an authorized role for " + step.getId() + ".");
                    }
                    case "APPROVAL" -> {
                        step.setForm("forms/" + step.getId() + ".json");
                        step.setAssignee(migrationReviewer());
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Confirm approver routing, approval outcomes and SLA for " + step.getId() + ".");
                    }
                    case "SERVICE_TASK" -> {
                        String actionName = "migration.stub." + key + "." + step.getId();
                        step.setAction(actionName);
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Replace no-op stub '" + actionName + "' with the approved integration for " + step.getId() + ".");
                    }
                    case "END" -> {
                        report.incrementConverted();
                        report.recordElement(step.getId(), "CONVERTED", rule.confidence(), rule.reason());
                    }
                    case "CHILD_WORKFLOW" -> {
                        step.setChildWorkflowKey(sourceStep.getChildWorkflowKey());
                        step.setChildWorkflowDepth(sourceStep.getChildWorkflowKey() == null ? 0 : 1);
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Verify child workflow key and callback behavior for " + step.getId() + ".");
                    }
                    default -> {
                        report.incrementUnsupported();
                        String reason = sourceStep.getManualReviewReason() == null
                                ? rule.reason() + " Source construct: '" + sourceStep.getStepType() + "'."
                                : sourceStep.getManualReviewReason();
                        // Preserve the source label for diagnostics; never invent an executable mapping.
                        step.setType(sourceStep.getStepType());
                        report.recordElement(step.getId(), "UNSUPPORTED", rule.confidence(), reason);
                        report.addActionItem("Resolve unsupported step " + step.getId() + ": " + reason);
                    }
                }
                stage.getSteps().add(step);
            }
            if (sourceStage == lastPrimaryStage && stage.getResolution() == null) {
                stage.setResolution("Completed");
                report.addActionItem("Confirm that the generated terminal resolution 'Completed' matches the Blueprint outcome.");
            }
            wf.getStages().add(stage);
        }
        return wf;
    }

    private AssigneeDefinition migrationReviewer() {
        AssigneeDefinition assignee = new AssigneeDefinition();
        assignee.setRole("migration_reviewer");
        return assignee;
    }

    private String slug(String text) {
        return text == null ? "unnamed" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String camelCase(String text) {
        StringBuilder result = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isBlank()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? "unknownAction" : Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }
}
