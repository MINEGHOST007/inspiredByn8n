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
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setApiVersion("w6w/v1");
        wf.setKind("Workflow");

        WorkflowMetadata meta = new WorkflowMetadata();
        String key = slug(caseType.getName());
        meta.setKey(key);
        meta.setName(caseType.getName());
        meta.setVersion("1.0.0");
        meta.setTier(1);
        meta.setOwner("unassigned");
        meta.setSource("blueprint:" + blueprintId + ":" + key);
        meta.setDescription(caseType.getDescription());
        wf.setMetadata(meta);

        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("schema", "schemas/data.schema.json");
        wf.setData(data);
        // Personas are blueprint-wide and do not establish per-step ownership.
        // Do not invent role bindings from a PDF that does not show them.
        wf.setRoles(new ArrayList<>());
        report.addActionItem("Set the owning team and data classification before deployment.");
        report.addActionItem("Map Blueprint personas to approved W6W roles and bind each human task to an authorized role.");
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
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Assign an authorized role/user and review form fields for " + step.getId() + ".");
                    }
                    case "APPROVAL" -> {
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Confirm approver routing, approval outcomes and SLA for " + step.getId() + ".");
                    }
                    case "SERVICE_TASK" -> {
                        String actionName = key.replace('-', '.') + "." + camelCase(sourceStep.getName());
                        step.setAction(actionName);
                        report.incrementApproximated();
                        report.recordElement(step.getId(), "APPROXIMATED", rule.confidence(), rule.reason());
                        report.addActionItem("Register and implement action '" + actionName + "' for " + step.getId() + ".");
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
            wf.getStages().add(stage);
        }
        return wf;
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
