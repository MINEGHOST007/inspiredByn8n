package com.w6w.compiler;

import com.w6w.model.*;
import java.util.*;

public class CwmToBpmnCompiler {

    public String compile(WorkflowDefinition workflow) {
        StringBuilder xml = new StringBuilder();
        String processKey = workflow.getMetadata().getKey();
        String processName = workflow.getMetadata().getName() != null ? workflow.getMetadata().getName() : processKey;

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        xml.append("             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n");
        xml.append("             targetNamespace=\"http://w6w.io/workflows\">\n\n");

        xml.append("  <process id=\"").append(escape(processKey)).append("\" ")
           .append("name=\"").append(escape(processName)).append("\" ")
           .append("isExecutable=\"true\">\n\n");
        if (workflow.getMetadata().getVersion() != null && !workflow.getMetadata().getVersion().isBlank()) {
            xml.append("    <documentation>W6W CWM version: ")
               .append(escape(workflow.getMetadata().getVersion())).append("</documentation>\n\n");
        }

        xml.append("    <startEvent id=\"start_event\" name=\"Start\" />\n");
        xml.append("    <sequenceFlow id=\"flow_start_to_first\" sourceRef=\"start_event\" targetRef=\"")
           .append(getFirstNodeRef(workflow)).append("\" />\n\n");

        List<StageDefinition> primaryStages = new ArrayList<>();
        List<StageDefinition> alternateStages = new ArrayList<>();

        for (StageDefinition stage : workflow.getStages()) {
            if (stage.isAlternate()) {
                alternateStages.add(stage);
            } else {
                primaryStages.add(stage);
            }
        }

        // Generate primary stages
        for (int i = 0; i < primaryStages.size(); i++) {
            StageDefinition currentStage = primaryStages.get(i);
            StageDefinition nextStage = (i + 1 < primaryStages.size()) ? primaryStages.get(i + 1) : null;
            compileStage(xml, currentStage, nextStage, workflow);
        }

        // Generate alternate stages
        for (StageDefinition altStage : alternateStages) {
            compileStage(xml, altStage, null, workflow);
        }

        xml.append("  </process>\n");
        xml.append("</definitions>\n");

        return xml.toString();
    }

    private String getFirstNodeRef(WorkflowDefinition workflow) {
        if (!workflow.getStages().isEmpty() && !workflow.getStages().get(0).getSteps().isEmpty()) {
            return entryNodeRef(workflow.getStages().get(0).getSteps().get(0));
        }
        return "end_default";
    }

    private void compileStage(StringBuilder xml, StageDefinition stage, StageDefinition nextStage, WorkflowDefinition workflow) {
        xml.append("    <!-- Stage: ").append(escape(stage.getName() != null ? stage.getName() : stage.getId())).append(" -->\n");
        List<StepDefinition> steps = stage.getSteps();

        for (int i = 0; i < steps.size(); i++) {
            StepDefinition step = steps.get(i);
            String stepRef = "step_" + sanitize(step.getId());
            String nextRef = null;

            if (i + 1 < steps.size()) {
                nextRef = entryNodeRef(steps.get(i + 1));
            } else if (nextStage != null && !nextStage.getSteps().isEmpty()) {
                nextRef = entryNodeRef(nextStage.getSteps().get(0));
            } else {
                nextRef = "end_" + sanitize(stage.getId());
            }

            compileConditionGate(xml, step, stepRef, nextRef);
            compileStep(xml, step, stepRef, nextRef, stage, workflow);
        }

        boolean explicitTerminalStep = !steps.isEmpty() && "end".equals(steps.get(steps.size() - 1).getType());
        if (nextStage == null && !explicitTerminalStep) {
            if (stage.getResolution() == null || stage.getResolution().isBlank()) {
                throw new IllegalArgumentException("Terminal stage '" + stage.getId() + "' must declare a resolution");
            }
            String endId = "end_" + sanitize(stage.getId());
            xml.append("    <endEvent id=\"").append(endId).append("\" name=\"Resolution: ")
               .append(escape(stage.getResolution() != null ? stage.getResolution() : "Complete")).append("\" />\n\n");
        }
    }

    private void compileStep(StringBuilder xml, StepDefinition step, String stepRef, String defaultNextRef, StageDefinition stage, WorkflowDefinition workflow) {
        String name = step.getName() != null ? step.getName() : step.getId();

        if ("userTask".equals(step.getType()) || "approval".equals(step.getType())) {
            xml.append("    <userTask id=\"").append(stepRef).append("\" name=\"").append(escape(name)).append("\"");
            if (step.getForm() != null) {
                xml.append(" flowable:formKey=\"").append(escape(step.getForm())).append("\"");
            }
            if (step.getSla() != null && step.getSla().getDeadline() != null && !step.getSla().getDeadline().isBlank()) {
                try { java.time.Duration.parse(step.getSla().getDeadline()); }
                catch (java.time.format.DateTimeParseException e) {
                    throw new IllegalArgumentException("SLA deadline for step '" + step.getId() + "' must be an ISO-8601 duration", e);
                }
                xml.append(" flowable:dueDate=\"${w6wSlaService.deadline('")
                   .append(escape(step.getSla().getDeadline())).append("')}\"");
            }
            if (step.getAssignee() != null) {
                if (step.getAssignee().getRole() != null) {
                    xml.append(" flowable:candidateGroups=\"").append(escape(step.getAssignee().getRole())).append("\"");
                } else if (step.getAssignee().getUser() != null) {
                    xml.append(" flowable:assignee=\"").append(escape(step.getAssignee().getUser())).append("\"");
                } else if (step.getAssignee().getLookup() != null) {
                    xml.append(" flowable:assignee=\"${w6wDirectoryService.lookup('").append(escape(step.getAssignee().getLookup())).append("', execution)}\"");
                }
            }
            xml.append(" />\n");
            compileSlaEscalation(xml, step, stepRef);

        } else if ("serviceTask".equals(step.getType())) {
            xml.append("    <serviceTask id=\"").append(stepRef).append("\" name=\"").append(escape(name)).append("\"")
               .append(" flowable:async=\"true\"")
               .append(" flowable:delegateExpression=\"${w6wActionExecutor}\">\n")
               .append("      <extensionElements>\n")
               .append("        <flowable:failedJobRetryTimeCycle>").append(failedJobRetryTimeCycle(step)).append("</flowable:failedJobRetryTimeCycle>\n")
               .append("        <flowable:field name=\"actionName\" stringValue=\"").append(escape(step.getAction() != null ? step.getAction() : "")).append("\" />\n")
               .append("        <flowable:field name=\"stepId\" stringValue=\"").append(escape(step.getId())).append("\" />\n")
               .append("      </extensionElements>\n")
               .append("    </serviceTask>\n");

        } else if ("decision".equals(step.getType())) {
            String decisionKey = versionedDecisionKey(step.getDecision(), workflow.getMetadata().getVersion());
            xml.append("    <serviceTask id=\"").append(stepRef).append("\" name=\"").append(escape(name)).append("\"")
                    .append(" flowable:async=\"true\"")
                    .append(" flowable:delegateExpression=\"${w6wDecisionExecutor}\">\n")
                    .append("      <extensionElements>\n")
                    .append("        <flowable:failedJobRetryTimeCycle>").append(failedJobRetryTimeCycle(step)).append("</flowable:failedJobRetryTimeCycle>\n")
                    .append("        <flowable:field name=\"decisionKey\" stringValue=\"").append(escape(decisionKey)).append("\" />\n")
                    .append("        <flowable:field name=\"stepId\" stringValue=\"").append(escape(step.getId())).append("\" />\n")
                    .append("        <flowable:field name=\"allowedOutcomes\" stringValue=\"").append(escape(String.join(",", step.getOutcomes().keySet()))).append("\" />\n")
                    .append("      </extensionElements>\n")
                    .append("    </serviceTask>\n");

        } else if ("timer".equals(step.getType())) {
            try {
                java.time.Duration duration = java.time.Duration.parse(step.getDuration());
                if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("Timer duration must be greater than zero");
            } catch (java.time.format.DateTimeParseException | NullPointerException e) {
                throw new IllegalArgumentException("Timer step '" + step.getId() + "' needs a positive ISO-8601 duration", e);
            }
            xml.append("    <intermediateCatchEvent id=\"").append(stepRef).append("\" name=\"").append(escape(name)).append("\">\n")
               .append("      <timerEventDefinition><timeDuration>").append(escape(step.getDuration())).append("</timeDuration></timerEventDefinition>\n")
               .append("    </intermediateCatchEvent>\n");

        } else if ("end".equals(step.getType())) {
            String resolution = stage.getResolution();
            String endName = resolution == null || resolution.isBlank() ? name : "Resolution: " + resolution;
            xml.append("    <endEvent id=\"").append(stepRef).append("\" name=\"").append(escape(endName)).append("\" />\n");
            return;
        } else {
            throw new IllegalArgumentException("Unsupported CWM step type '" + step.getType()
                    + "' for step '" + step.getId() + "'; refusing to emit a user task as a fallback");
        }

        // Handle outcomes & gateways
        if (step.getOutcomes() != null && !step.getOutcomes().isEmpty()) {
            String gatewayId = "gw_" + sanitize(step.getId());
            xml.append("    <exclusiveGateway id=\"").append(gatewayId).append("\" name=\"Outcomes: ").append(escape(name)).append("\" />\n");
            xml.append("    <sequenceFlow id=\"flow_").append(stepRef).append("_to_gw\" sourceRef=\"").append(stepRef).append("\" targetRef=\"").append(gatewayId).append("\" />\n");

            for (Map.Entry<String, String> outcome : step.getOutcomes().entrySet()) {
                String outcomeName = outcome.getKey();
                String target = outcome.getValue();
                String targetRef = resolveTargetRef(target, defaultNextRef, workflow);
                String flowId = "flow_" + gatewayId + "_" + sanitize(outcomeName);

                xml.append("    <sequenceFlow id=\"").append(flowId).append("\" sourceRef=\"").append(gatewayId).append("\" targetRef=\"").append(targetRef).append("\">\n");
                xml.append("      <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${execution.getVariable('outcome') == '").append(escape(outcomeName)).append("'}]]></conditionExpression>\n");
                xml.append("    </sequenceFlow>\n");
            }
            xml.append("\n");
        } else {
            xml.append("    <sequenceFlow id=\"flow_").append(stepRef).append("_next\" sourceRef=\"").append(stepRef).append("\" targetRef=\"").append(defaultNextRef).append("\" />\n\n");
        }
    }

    private void compileSlaEscalation(StringBuilder xml, StepDefinition step, String taskRef) {
        if (step.getSla() == null || step.getSla().getOnDeadline() == null || step.getSla().getOnDeadline().isBlank()) return;
        String action = step.getSla().getOnDeadline().trim();
        if (!action.startsWith("escalate:"))
            throw new IllegalArgumentException("SLA onDeadline for step '" + step.getId() + "' must use 'escalate:<role>' in CWM v1");
        String role = action.substring("escalate:".length()).trim();
        if (!role.matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("SLA escalation role for step '" + step.getId() + "' must be a role identifier");
        String deadline = step.getSla().getDeadline();
        try {
            java.time.Duration duration = java.time.Duration.parse(deadline);
            if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("SLA deadline must be greater than zero");
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("SLA escalation for step '" + step.getId() + "' needs a positive ISO-8601 deadline", e);
        }

        String suffix = sanitize(step.getId());
        String boundaryId = "sla_deadline_" + suffix;
        String escalationId = "sla_escalate_" + suffix;
        String endId = "sla_end_" + suffix;
        xml.append("    <boundaryEvent id=\"").append(boundaryId).append("\" attachedToRef=\"").append(taskRef).append("\" cancelActivity=\"false\">\n")
           .append("      <timerEventDefinition><timeDuration>").append(escape(deadline)).append("</timeDuration></timerEventDefinition>\n")
           .append("    </boundaryEvent>\n")
           .append("    <sequenceFlow id=\"flow_").append(boundaryId).append("_to_escalation\" sourceRef=\"").append(boundaryId).append("\" targetRef=\"").append(escalationId).append("\" />\n")
           .append("    <serviceTask id=\"").append(escalationId).append("\" name=\"Escalate overdue task\" flowable:delegateExpression=\"${w6wSlaEscalationExecutor}\">\n")
           .append("      <extensionElements>\n")
           .append("        <flowable:field name=\"taskDefinitionKey\" stringValue=\"").append(escape(taskRef)).append("\" />\n")
           .append("        <flowable:field name=\"targetRole\" stringValue=\"").append(escape(role)).append("\" />\n")
           .append("      </extensionElements>\n")
           .append("    </serviceTask>\n")
           .append("    <sequenceFlow id=\"flow_").append(escalationId).append("_to_end\" sourceRef=\"").append(escalationId).append("\" targetRef=\"").append(endId).append("\" />\n")
           .append("    <endEvent id=\"").append(endId).append("\" name=\"SLA escalation complete\" />\n");
    }

    private String resolveTargetRef(String target, String defaultRef, WorkflowDefinition workflow) {
        if ("next".equals(target)) {
            return defaultRef;
        }
        if (target.startsWith("stage:")) {
            String stageId = target.substring(6);
            for (StageDefinition st : workflow.getStages()) {
                if (stageId.equalsIgnoreCase(st.getId()) && !st.getSteps().isEmpty()) {
                    return entryNodeRef(st.getSteps().get(0));
                }
            }
        }
        if (target.startsWith("end:")) {
            return "end_" + sanitize(target.substring(4));
        }
        return defaultRef;
    }

    private void compileConditionGate(StringBuilder xml, StepDefinition step, String stepRef, String defaultNextRef) {
        if (step.getCondition() == null) return;
        String gateId = conditionGatewayId(step);
        String skipFlowId = "flow_" + gateId + "_skip";
        xml.append("    <exclusiveGateway id=\"").append(gateId).append("\" name=\"Condition: ")
                .append(escape(step.getName() != null ? step.getName() : step.getId()))
                .append("\" default=\"").append(skipFlowId).append("\" />\n")
                .append("    <sequenceFlow id=\"flow_").append(gateId).append("_run\" sourceRef=\"")
                .append(gateId).append("\" targetRef=\"").append(stepRef).append("\">\n")
                .append("      <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${")
                .append(compileCondition(step.getCondition())).append("}]]></conditionExpression>\n")
                .append("    </sequenceFlow>\n")
                .append("    <sequenceFlow id=\"").append(skipFlowId).append("\" sourceRef=\"")
                .append(gateId).append("\" targetRef=\"").append(defaultNextRef).append("\" />\n");
    }

    private String compileCondition(com.w6w.model.ConditionDefinition condition) {
        if (condition == null) throw new IllegalArgumentException("Condition expression is missing");
        List<String> groups = new ArrayList<>();
        if (condition.getAnd() != null && !condition.getAnd().isEmpty())
            groups.add("(" + String.join(" && ", condition.getAnd().stream().map(this::compileCondition).toList()) + ")");
        if (condition.getOr() != null && !condition.getOr().isEmpty())
            groups.add("(" + String.join(" || ", condition.getOr().stream().map(this::compileCondition).toList()) + ")");
        if (condition.getNot() != null) groups.add("!(" + compileCondition(condition.getNot()) + ")");
        if (condition.getField() != null && !condition.getField().isBlank()) groups.add(compileFieldCondition(condition));
        if (groups.isEmpty()) throw new IllegalArgumentException("Condition must declare a field or a non-empty and/or/not group");
        if (groups.size() > 1) throw new IllegalArgumentException("A condition cannot combine a field with and/or/not at the same level");
        return groups.get(0);
    }

    private String compileFieldCondition(com.w6w.model.ConditionDefinition condition) {
        String field = condition.getField().replaceFirst("^(data|variables)\\.", "");
        if (!field.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Condition field must reference a top-level data property: " + condition.getField());
        String value = "execution.getVariable('" + field + "')";
        String operator = condition.getOperator();
        Object expected = condition.getValue();
        return switch (operator == null ? "" : operator) {
            case "exists" -> value + " != null";
            case "eq" -> value + " == " + expressionLiteral(expected);
            case "ne" -> value + " != " + expressionLiteral(expected);
            case "lt" -> value + " < " + expressionLiteral(expected);
            case "lte" -> value + " <= " + expressionLiteral(expected);
            case "gt" -> value + " > " + expressionLiteral(expected);
            case "gte" -> value + " >= " + expressionLiteral(expected);
            case "in" -> compileIn(value, expected);
            default -> throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        };
    }

    private String compileIn(String fieldExpression, Object expected) {
        if (!(expected instanceof List<?> values) || values.isEmpty())
            throw new IllegalArgumentException("Condition operator 'in' requires a non-empty array value");
        return "(" + String.join(" || ", values.stream()
                .map(value -> fieldExpression + " == " + expressionLiteral(value)).toList()) + ")";
    }

    private String failedJobRetryTimeCycle(StepDefinition step) {
        Map<String, Object> policy = step.getOnError();
        int retries = 3;
        String backoff = "PT1M";
        if (policy != null) {
            Object configuredRetries = policy.get("retries");
            Object configuredBackoff = policy.get("backoff");
            Object onFailure = policy.get("onFailure");
            if (configuredRetries instanceof Number number) retries = number.intValue();
            if (configuredBackoff instanceof String duration) backoff = duration;
            if (onFailure != null && !"deadLetter".equals(onFailure))
                throw new IllegalArgumentException("Unsupported onError.onFailure for step '" + step.getId() + "': " + onFailure);
        }
        if (retries < 1 || retries > 100)
            throw new IllegalArgumentException("onError.retries for step '" + step.getId() + "' must be an integer from 1 to 100");
        try {
            java.time.Duration delay = java.time.Duration.parse(backoff);
            if (delay.isZero() || delay.isNegative()) throw new IllegalArgumentException("Backoff must be greater than zero");
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("onError.backoff for step '" + step.getId() + "' must be an ISO-8601 duration", e);
        }
        return "R" + retries + "/" + backoff;
    }

    private String expressionLiteral(Object value) {
        if (value instanceof String string) return "'" + string.replace("\\", "\\\\").replace("'", "\\'") + "'";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        throw new IllegalArgumentException("Condition value must be a string, number, or boolean");
    }

    private String entryNodeRef(StepDefinition step) {
        return step.getCondition() == null ? "step_" + sanitize(step.getId()) : conditionGatewayId(step);
    }

    private String conditionGatewayId(StepDefinition step) { return "gw_condition_" + sanitize(step.getId()); }

    private String sanitize(String id) {
        return id != null ? id.replace("-", "_").replace(".", "_") : "node";
    }

    public static String versionedDecisionKey(String decisionPath, String workflowVersion) {
        if (decisionPath == null || decisionPath.isBlank()) throw new IllegalArgumentException("Decision step has no decision file reference");
        String fileName = decisionPath.replace('\\', '/').substring(decisionPath.replace('\\', '/').lastIndexOf('/') + 1);
        String key = fileName.replaceFirst("(?i)\\.(yaml|yml)$", "");
        if (!key.matches("[a-z0-9]+(-[a-z0-9]+)*")) throw new IllegalArgumentException("Decision filename must use its kebab-case table key");
        if (workflowVersion == null || !workflowVersion.matches("\\d+\\.\\d+\\.\\d+")) throw new IllegalArgumentException("Decision workflow version must be semantic versioning");
        return key + "_v" + workflowVersion.replace('.', '_');
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
