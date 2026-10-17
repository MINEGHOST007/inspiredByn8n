package com.w6w.compiler;

import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;

import java.util.ArrayList;
import java.util.List;

public class CwmLinter {

    public static class LintIssue {
        private final String level; // ERROR, WARNING
        private final String message;

        public LintIssue(String level, String message) {
            this.level = level;
            this.message = message;
        }

        public String getLevel() { return level; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "[" + level + "] " + message;
        }
    }

    public static class LintResult {
        private final List<LintIssue> issues = new ArrayList<>();

        public void addError(String message) { issues.add(new LintIssue("ERROR", message)); }
        public void addWarning(String message) { issues.add(new LintIssue("WARNING", message)); }
        public boolean hasErrors() { return issues.stream().anyMatch(i -> "ERROR".equals(i.getLevel())); }
        public List<LintIssue> getIssues() { return issues; }
    }

    public LintResult lint(WorkflowDefinition workflow) {
        LintResult result = new LintResult();

        if (workflow.getMetadata() == null || workflow.getMetadata().getKey() == null) {
            result.addError("Missing metadata.key");
            return result;
        }
        if (workflow.getMetadata().getVersion() == null || workflow.getMetadata().getVersion().isBlank()) {
            result.addError("Missing metadata.version");
        }
        if (workflow.getMetadata().getOwner() == null || workflow.getMetadata().getOwner().isBlank()
                || "unassigned".equalsIgnoreCase(workflow.getMetadata().getOwner())) {
            result.addError("Missing approved metadata.owner");
        }
        if (workflow.getMetadata().getDataClassification() == null || workflow.getMetadata().getDataClassification().isBlank()) {
            result.addError("Missing metadata.dataClassification");
        }

        if (workflow.getStages() == null || workflow.getStages().isEmpty()) {
            result.addError("Workflow has no stages");
            return result;
        }

        boolean hasResolutionStage = false;
        StageDefinition lastPrimaryStage = null;

        for (StageDefinition stage : workflow.getStages()) {
            if (stage.getId() == null || stage.getId().isEmpty()) {
                result.addError("Stage is missing an id");
            }

            if (stage.getResolution() != null) {
                hasResolutionStage = true;
            }
            if (stage.isAlternate() && (stage.getResolution() == null || stage.getResolution().isBlank())) {
                result.addError("Alternate stage '" + stage.getId() + "' must declare its terminal resolution");
            }
            if (!stage.isAlternate()) lastPrimaryStage = stage;

            if (stage.getSteps() == null || stage.getSteps().isEmpty()) {
                result.addWarning("Stage '" + stage.getId() + "' has no steps");
                continue;
            }

            for (StepDefinition step : stage.getSteps()) {
                if (step.getId() == null) {
                    result.addError("Step in stage '" + stage.getId() + "' is missing an id");
                }

                if ("userTask".equals(step.getType()) || "approval".equals(step.getType())) {
                    if (step.getAssignee() == null || 
                        (step.getAssignee().getRole() == null && step.getAssignee().getUser() == null && step.getAssignee().getLookup() == null)) {
                        result.addError("Human task '" + step.getId() + "' in stage '" + stage.getId() + "' has no assignee (role/user/lookup)");
                    }
                    if (step.getForm() == null) {
                        result.addWarning("Human task '" + step.getId() + "' has no form schema reference");
                    }
                }

                if ("serviceTask".equals(step.getType())) {
                    if (step.getAction() == null || step.getAction().isEmpty()) {
                        result.addError("Service task '" + step.getId() + "' in stage '" + stage.getId() + "' has no registered action name");
                    }
                }

                if (step.getType() == null || !java.util.Set.of("userTask", "approval", "serviceTask", "decision", "parallel",
                        "subStage", "waitEvent", "timer", "end", "childWorkflow")
                        .contains(step.getType())) {
                    result.addError("Unsupported step type '" + step.getType() + "' for step '" + step.getId() + "'");
                }
                if (("decision".equals(step.getType()) || "parallel".equals(step.getType()))
                        && (step.getOutcomes() == null || step.getOutcomes().isEmpty())) {
                    result.addError("Step '" + step.getId() + "' of type '" + step.getType()
                            + "' must declare explicit outcomes before compilation");
                }
                if ("decision".equals(step.getType()) && (step.getDecision() == null || step.getDecision().isBlank())) {
                    result.addError("Decision step '" + step.getId() + "' must reference a decision table file");
                }
                if ("decision".equals(step.getType()) && step.getOutcomes() != null
                        && step.getOutcomes().keySet().stream().anyMatch(outcome -> outcome.contains(","))) {
                    result.addError("Decision step '" + step.getId() + "' outcome labels cannot contain commas");
                }
            }
        }

        if (!hasResolutionStage) {
            result.addError("Workflow '" + workflow.getMetadata().getKey() + "' has no stage declaring a terminal resolution state");
        } else if (lastPrimaryStage != null && (lastPrimaryStage.getResolution() == null || lastPrimaryStage.getResolution().isBlank())) {
            result.addError("Final primary stage '" + lastPrimaryStage.getId() + "' must declare its terminal resolution");
        }

        return result;
    }
}
