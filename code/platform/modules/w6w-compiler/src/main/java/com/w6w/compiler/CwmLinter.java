package com.w6w.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w6w.model.ConditionDefinition;
import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/** Static checks for the subset of CWM v1 that this runtime can execute. */
public class CwmLinter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> EXECUTABLE_TYPES = Set.of("userTask", "approval", "serviceTask", "decision", "timer", "end");
    private static final Set<String> KNOWN_TYPES = Set.of("userTask", "approval", "serviceTask", "decision",
            "parallel", "subStage", "waitEvent", "timer", "childWorkflow", "end");

    public static class LintIssue {
        private final String level;
        private final String message;

        public LintIssue(String level, String message) { this.level = level; this.message = message; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }

        @Override public String toString() { return "[" + level + "] " + message; }
    }

    public static class LintResult {
        private final List<LintIssue> issues = new ArrayList<>();
        public void addError(String message) { issues.add(new LintIssue("ERROR", message)); }
        public void addWarning(String message) { issues.add(new LintIssue("WARNING", message)); }
        public boolean hasErrors() { return issues.stream().anyMatch(i -> "ERROR".equals(i.getLevel())); }
        public List<LintIssue> getIssues() { return issues; }
    }

    public LintResult lint(WorkflowDefinition workflow) {
        return lint(workflow, null);
    }

    /** Lints the workflow and resolves data-field references against its package schema when a source file is known. */
    public LintResult lint(WorkflowDefinition workflow, Path workflowFile) {
        LintResult result = new LintResult();
        if (workflow == null) {
            result.addError("Workflow definition is missing");
            return result;
        }
        if (!"w6w/v1".equals(workflow.getApiVersion())) result.addError("apiVersion must be 'w6w/v1'");
        if (!"Workflow".equals(workflow.getKind())) result.addError("kind must be 'Workflow'");
        if (workflow.getMetadata() == null || workflow.getMetadata().getKey() == null
                || workflow.getMetadata().getKey().isBlank()) {
            result.addError("Missing metadata.key");
            return result;
        }
        if (workflow.getMetadata().getVersion() == null || workflow.getMetadata().getVersion().isBlank())
            result.addError("Missing metadata.version");
        if (workflow.getMetadata().getOwner() == null || workflow.getMetadata().getOwner().isBlank()
                || "unassigned".equalsIgnoreCase(workflow.getMetadata().getOwner()))
            result.addError("Missing approved metadata.owner");
        if (workflow.getMetadata().getDataClassification() == null || workflow.getMetadata().getDataClassification().isBlank())
            result.addError("Missing metadata.dataClassification");

        List<StageDefinition> stages = workflow.getStages();
        if (stages == null || stages.isEmpty()) {
            result.addError("Workflow has no stages");
            return result;
        }

        Map<String, StageDefinition> stagesById = new LinkedHashMap<>();
        Map<String, StepDefinition> stepsById = new LinkedHashMap<>();
        Map<String, String> defaultNext = new HashMap<>();
        Set<String> duplicateStepIds = new HashSet<>();
        Set<String> duplicateStageIds = new HashSet<>();
        boolean hasResolutionStage = false;
        StageDefinition lastPrimaryStage = null;

        for (StageDefinition stage : stages) {
            if (stage == null) {
                result.addError("Workflow contains a null stage");
                continue;
            }
            String stageId = stage.getId();
            if (stageId == null || stageId.isBlank()) {
                result.addError("Stage is missing an id");
            } else if (stagesById.putIfAbsent(stageId.toLowerCase(Locale.ROOT), stage) != null) {
                duplicateStageIds.add(stageId.toLowerCase(Locale.ROOT));
                result.addError("Duplicate stage id '" + stageId + "'");
            }
            if (stage.getResolution() != null && !stage.getResolution().isBlank()) hasResolutionStage = true;
            if (stage.isAlternate() && (stage.getResolution() == null || stage.getResolution().isBlank()))
                result.addError("Alternate stage '" + stageId + "' must declare its terminal resolution");
            if (!stage.isAlternate()) lastPrimaryStage = stage;
            if (stage.getSteps() == null || stage.getSteps().isEmpty()) {
                result.addError("Stage '" + stageId + "' has no steps");
                continue;
            }
            for (StepDefinition step : stage.getSteps()) {
                if (step == null) {
                    result.addError("Stage '" + stageId + "' contains a null step");
                    continue;
                }
                String stepId = step.getId();
                if (stepId == null || stepId.isBlank()) {
                    result.addError("Step in stage '" + stageId + "' is missing an id");
                    continue;
                }
                if (stepsById.putIfAbsent(stepId, step) != null) {
                    duplicateStepIds.add(stepId);
                    result.addError("Duplicate step id '" + stepId + "'");
                }
            }
        }

        if (stages.get(0) != null && stages.get(0).isAlternate())
            result.addError("The first workflow stage cannot be alternate");
        if (!hasResolutionStage) {
            result.addError("Workflow '" + workflow.getMetadata().getKey() + "' has no stage declaring a terminal resolution state");
        } else if (lastPrimaryStage != null
                && (lastPrimaryStage.getResolution() == null || lastPrimaryStage.getResolution().isBlank())) {
            result.addError("Final primary stage '" + lastPrimaryStage.getId() + "' must declare its terminal resolution");
        }

        List<StageDefinition> primaryStages = stages.stream().filter(s -> s != null && !s.isAlternate()).toList();
        for (int stageIndex = 0; stageIndex < primaryStages.size(); stageIndex++) {
            StageDefinition stage = primaryStages.get(stageIndex);
            List<StepDefinition> stageSteps = stage.getSteps() == null ? List.of() : stage.getSteps();
            for (int stepIndex = 0; stepIndex < stageSteps.size(); stepIndex++) {
                StepDefinition step = stageSteps.get(stepIndex);
                if (step == null || step.getId() == null) continue;
                String next = stepIndex + 1 < stageSteps.size() ? idOf(stageSteps.get(stepIndex + 1))
                        : stageIndex + 1 < primaryStages.size() && !primaryStages.get(stageIndex + 1).getSteps().isEmpty()
                        ? idOf(primaryStages.get(stageIndex + 1).getSteps().get(0)) : null;
                if (next != null) defaultNext.putIfAbsent(step.getId(), next);
            }
        }
        for (StageDefinition stage : stages) {
            if (stage == null || !stage.isAlternate() || stage.getSteps() == null) continue;
            List<StepDefinition> stageSteps = stage.getSteps();
            for (int i = 0; i + 1 < stageSteps.size(); i++) {
                if (stageSteps.get(i) != null && stageSteps.get(i).getId() != null)
                    defaultNext.putIfAbsent(stageSteps.get(i).getId(), idOf(stageSteps.get(i + 1)));
            }
        }

        for (StageDefinition stage : stages) {
            if (stage == null || stage.getSteps() == null) continue;
            for (StepDefinition step : stage.getSteps()) {
                if (step == null || step.getId() == null || step.getId().isBlank()) continue;
                lintStep(step, stage, stagesById, result);
            }
        }

        lintDataReferences(workflow, workflowFile, stages, result);

        if (stepsById.size() > 0 && duplicateStepIds.isEmpty() && duplicateStageIds.isEmpty())
            lintReachability(stages, stagesById, stepsById, defaultNext, result);
        return result;
    }

    private void lintDataReferences(WorkflowDefinition workflow, Path workflowFile, List<StageDefinition> stages,
                                    LintResult result) {
        boolean hasConditions = stages.stream().filter(s -> s != null && s.getSteps() != null)
                .flatMap(s -> s.getSteps().stream()).filter(step -> step != null)
                .anyMatch(step -> step.getCondition() != null);
        if (!hasConditions) return;
        if (workflowFile == null) return;

        Object schemaPathValue = workflow.getData() == null ? null : workflow.getData().get("schema");
        if (!(schemaPathValue instanceof String schemaPath) || schemaPath.isBlank()) {
            result.addError("Workflow conditions reference data but workflow.data.schema is not declared");
            return;
        }
        Path packageRoot = workflowFile.toAbsolutePath().normalize().getParent();
        Path schemaFile = packageRoot.resolve(schemaPath).normalize();
        if (!schemaFile.startsWith(packageRoot) || !Files.isRegularFile(schemaFile)) {
            result.addError("Workflow data schema is missing or escapes the package: " + schemaPath);
            return;
        }

        final JsonNode schema;
        try {
            schema = JSON.readTree(schemaFile.toFile());
        } catch (IOException e) {
            result.addError("Workflow data schema cannot be read: " + schemaPath + " (" + e.getMessage() + ")");
            return;
        }
        for (StageDefinition stage : stages) {
            if (stage == null || stage.getSteps() == null) continue;
            for (StepDefinition step : stage.getSteps()) {
                if (step != null && step.getCondition() != null)
                    lintConditionFields(step.getCondition(), schema, step.getId(), result);
            }
        }
    }

    private void lintConditionFields(ConditionDefinition condition, JsonNode schema, String stepId, LintResult result) {
        if (condition == null) return;
        if (!isBlank(condition.getField())) {
            String field = condition.getField().replaceFirst("^(data|variables)\\.", "");
            JsonNode current = schema;
            boolean found = true;
            for (String segment : field.split("\\.")) {
                current = current == null ? null : current.path("properties").get(segment);
                if (current == null || current.isMissingNode()) { found = false; break; }
            }
            if (!found) result.addError("Step '" + stepId + "' condition references undefined data field '"
                    + condition.getField() + "'");
        }
        if (condition.getAnd() != null) condition.getAnd().forEach(child -> lintConditionFields(child, schema, stepId, result));
        if (condition.getOr() != null) condition.getOr().forEach(child -> lintConditionFields(child, schema, stepId, result));
        if (condition.getNot() != null) lintConditionFields(condition.getNot(), schema, stepId, result);
    }

    private void lintStep(StepDefinition step, StageDefinition stage, Map<String, StageDefinition> stagesById,
                          LintResult result) {
        String type = step.getType();
        if (type == null || !KNOWN_TYPES.contains(type)) {
            result.addError("Unsupported step type '" + type + "' for step '" + step.getId() + "'");
        } else if (!EXECUTABLE_TYPES.contains(type)) {
            result.addError("Step type '" + type + "' for step '" + step.getId() + "' is not executable by the v1 runtime");
        }
        if ("userTask".equals(type) || "approval".equals(type)) {
            if (step.getAssignee() == null || isBlank(step.getAssignee().getRole())
                    && isBlank(step.getAssignee().getUser()) && isBlank(step.getAssignee().getLookup()))
                result.addError("Human task '" + step.getId() + "' in stage '" + stage.getId() + "' has no assignee (role/user/lookup)");
            if (step.getForm() == null) result.addWarning("Human task '" + step.getId() + "' has no form schema reference");
        }
        if ("serviceTask".equals(type) && isBlank(step.getAction()))
            result.addError("Service task '" + step.getId() + "' in stage '" + stage.getId() + "' has no registered action name");
        if ("timer".equals(type)) lintTimer(step, result);
        lintErrorPolicy(step, result);
        if (("decision".equals(type) || "parallel".equals(type))
                && (step.getOutcomes() == null || step.getOutcomes().isEmpty()))
            result.addError("Step '" + step.getId() + "' of type '" + type + "' must declare explicit outcomes before compilation");
        if ("decision".equals(type) && isBlank(step.getDecision()))
            result.addError("Decision step '" + step.getId() + "' must reference a decision table file");
        if ("decision".equals(type) && step.getOutcomes() != null
                && step.getOutcomes().keySet().stream().anyMatch(outcome -> outcome.contains(",")))
            result.addError("Decision step '" + step.getId() + "' outcome labels cannot contain commas");
        if (step.getCondition() != null) lintCondition(step.getCondition(), step.getId(), result);

        if (step.getOutcomes() == null) return;
        step.getOutcomes().forEach((outcome, target) -> {
            if (isBlank(outcome) || isBlank(target)) {
                result.addError("Step '" + step.getId() + "' has an outcome with an empty label or target");
            } else if ("next".equals(target)) {
                // The compiler resolves this to the next step or the stage's terminal event.
            } else if (target.startsWith("stage:")) {
                String targetStageId = target.substring("stage:".length()).toLowerCase(Locale.ROOT);
                StageDefinition targetStage = stagesById.get(targetStageId);
                if (targetStage == null) result.addError("Step '" + step.getId() + "' outcome '" + outcome
                        + "' references undefined stage '" + target.substring("stage:".length()) + "'");
                else if (targetStage.getSteps() == null || targetStage.getSteps().isEmpty())
                    result.addError("Step '" + step.getId() + "' outcome '" + outcome
                            + "' references stage '" + targetStage.getId() + "' with no steps");
            } else if (target.startsWith("end:")) {
                String targetStageId = target.substring("end:".length()).toLowerCase(Locale.ROOT);
                StageDefinition targetStage = stagesById.get(targetStageId);
                if (targetStage == null || isBlank(targetStage.getResolution()))
                    result.addError("Step '" + step.getId() + "' outcome '" + outcome
                            + "' references undefined terminal stage '" + target.substring("end:".length()) + "'");
                else if (targetStage.getSteps() != null && !targetStage.getSteps().isEmpty()
                        && "end".equals(targetStage.getSteps().get(targetStage.getSteps().size() - 1).getType()))
                    result.addError("Step '" + step.getId() + "' outcome '" + outcome
                            + "' targets an implicit end event that is not generated for stage '" + targetStage.getId() + "'");
            } else {
                result.addError("Step '" + step.getId() + "' outcome '" + outcome + "' has invalid target '" + target + "'");
            }
        });
    }

    private void lintTimer(StepDefinition step, LintResult result) {
        if (isBlank(step.getDuration())) {
            result.addError("Timer step '" + step.getId() + "' must declare a duration");
            return;
        }
        try {
            if (Duration.parse(step.getDuration()).isZero() || Duration.parse(step.getDuration()).isNegative())
                result.addError("Timer step '" + step.getId() + "' duration must be greater than zero");
        } catch (DateTimeParseException e) {
            result.addError("Timer step '" + step.getId() + "' duration must be an ISO-8601 duration");
        }
    }

    private void lintErrorPolicy(StepDefinition step, LintResult result) {
        Map<String, Object> policy = step.getOnError();
        if (policy == null) return;
        if (!"serviceTask".equals(step.getType())) {
            result.addError("Step '" + step.getId() + "' can declare onError only when it is a serviceTask");
            return;
        }
        Set<String> allowed = Set.of("retries", "backoff", "onFailure");
        policy.keySet().stream().filter(key -> !allowed.contains(key))
                .forEach(key -> result.addError("Service task '" + step.getId() + "' has unsupported onError field '" + key + "'"));

        Object retries = policy.get("retries");
        if (retries != null && (!(retries instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < 1 || number.intValue() > 100))
            result.addError("Service task '" + step.getId() + "' onError.retries must be an integer from 1 to 100");

        Object backoff = policy.get("backoff");
        if (backoff != null) {
            if (!(backoff instanceof String value)) {
                result.addError("Service task '" + step.getId() + "' onError.backoff must be an ISO-8601 duration");
            } else {
                try {
                    if (Duration.parse(value).isZero() || Duration.parse(value).isNegative())
                        result.addError("Service task '" + step.getId() + "' onError.backoff must be greater than zero");
                } catch (DateTimeParseException e) {
                    result.addError("Service task '" + step.getId() + "' onError.backoff must be an ISO-8601 duration");
                }
            }
        }

        Object onFailure = policy.get("onFailure");
        if (onFailure != null && !"deadLetter".equals(onFailure))
            result.addError("Service task '" + step.getId() + "' onError.onFailure must be 'deadLetter' in CWM v1");
    }

    private void lintCondition(ConditionDefinition condition, String stepId, LintResult result) {
        if (condition == null) return;
        boolean hasField = !isBlank(condition.getField());
        boolean hasGroup = condition.getAnd() != null || condition.getOr() != null || condition.getNot() != null;
        int groupCount = (condition.getAnd() == null ? 0 : 1) + (condition.getOr() == null ? 0 : 1)
                + (condition.getNot() == null ? 0 : 1);
        if (groupCount > 1)
            result.addError("Step '" + stepId + "' condition may use only one and/or/not group at a time");
        if (!hasField && !hasGroup) {
            result.addError("Step '" + stepId + "' condition must declare a field or a non-empty and/or/not group");
        } else if (hasField && hasGroup) {
            result.addError("Step '" + stepId + "' condition cannot combine a field with and/or/not at the same level");
        }
        if (hasField) {
            String field = condition.getField().replaceFirst("^(data|variables)\\.", "");
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*"))
                result.addError("Step '" + stepId + "' condition must reference a top-level data field: " + condition.getField());
            Set<String> operators = Set.of("eq", "ne", "lt", "lte", "gt", "gte", "in", "exists");
            if (isBlank(condition.getOperator()) || !operators.contains(condition.getOperator()))
                result.addError("Step '" + stepId + "' condition uses unsupported operator '" + condition.getOperator() + "'");
            Object value = condition.getValue();
            if ("in".equals(condition.getOperator())) {
                if (!(value instanceof List<?> values) || values.isEmpty()
                        || values.stream().anyMatch(item -> !(item instanceof String || item instanceof Number || item instanceof Boolean)))
                    result.addError("Step '" + stepId + "' condition operator 'in' requires a non-empty array of scalar values");
            } else if (!"exists".equals(condition.getOperator())
                    && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                result.addError("Step '" + stepId + "' condition requires a scalar value");
            }
        }
        if (condition.getAnd() != null) {
            if (condition.getAnd().isEmpty()) result.addError("Step '" + stepId + "' condition 'and' must not be empty");
            condition.getAnd().forEach(child -> lintCondition(child, stepId, result));
        }
        if (condition.getOr() != null) {
            if (condition.getOr().isEmpty()) result.addError("Step '" + stepId + "' condition 'or' must not be empty");
            condition.getOr().forEach(child -> lintCondition(child, stepId, result));
        }
        if (condition.getNot() != null) lintCondition(condition.getNot(), stepId, result);
    }

    private void lintReachability(List<StageDefinition> stages, Map<String, StageDefinition> stagesById,
                                  Map<String, StepDefinition> stepsById, Map<String, String> defaultNext,
                                  LintResult result) {
        StageDefinition initialStage = stages.get(0);
        if (initialStage == null || initialStage.getSteps() == null || initialStage.getSteps().isEmpty()) return;
        String initialStep = idOf(initialStage.getSteps().get(0));
        if (initialStep == null || !stepsById.containsKey(initialStep)) return;

        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(initialStep);
        while (!pending.isEmpty()) {
            String currentId = pending.removeFirst();
            if (!reachable.add(currentId)) continue;
            StepDefinition current = stepsById.get(currentId);
            if (current == null || "end".equals(current.getType())) continue;
            if (current.getOutcomes() == null || current.getOutcomes().isEmpty()) {
                addIfKnown(defaultNext.get(currentId), stepsById, pending);
                continue;
            }
            for (String target : current.getOutcomes().values()) {
                if ("next".equals(target)) addIfKnown(defaultNext.get(currentId), stepsById, pending);
                else if (target != null && target.startsWith("stage:")) {
                    StageDefinition targetStage = stagesById.get(target.substring("stage:".length()).toLowerCase(Locale.ROOT));
                    if (targetStage != null && targetStage.getSteps() != null && !targetStage.getSteps().isEmpty())
                        addIfKnown(idOf(targetStage.getSteps().get(0)), stepsById, pending);
                }
            }
        }
        stepsById.keySet().stream().filter(id -> !reachable.contains(id)).sorted()
                .forEach(id -> result.addError("Step '" + id + "' is unreachable from the workflow start"));
    }

    private static void addIfKnown(String id, Map<String, StepDefinition> stepsById, Deque<String> pending) {
        if (id != null && stepsById.containsKey(id)) pending.addLast(id);
    }

    private static String idOf(StepDefinition step) { return step == null ? null : step.getId(); }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
