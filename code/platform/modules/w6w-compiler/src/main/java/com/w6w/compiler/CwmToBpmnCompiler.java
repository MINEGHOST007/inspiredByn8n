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
            return "step_" + sanitize(workflow.getStages().get(0).getSteps().get(0).getId());
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
                nextRef = "step_" + sanitize(steps.get(i + 1).getId());
            } else if (nextStage != null && !nextStage.getSteps().isEmpty()) {
                nextRef = "step_" + sanitize(nextStage.getSteps().get(0).getId());
            } else {
                nextRef = "end_" + sanitize(stage.getId());
            }

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

        } else if ("serviceTask".equals(step.getType())) {
            xml.append("    <serviceTask id=\"").append(stepRef).append("\" name=\"").append(escape(name)).append("\"")
               .append(" flowable:delegateExpression=\"${w6wActionExecutor}\">\n")
               .append("      <extensionElements>\n")
               .append("        <flowable:field name=\"actionName\" stringValue=\"").append(escape(step.getAction() != null ? step.getAction() : "")).append("\" />\n")
               .append("        <flowable:field name=\"stepId\" stringValue=\"").append(escape(step.getId())).append("\" />\n")
               .append("      </extensionElements>\n")
               .append("    </serviceTask>\n");

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

    private String resolveTargetRef(String target, String defaultRef, WorkflowDefinition workflow) {
        if ("next".equals(target)) {
            return defaultRef;
        }
        if (target.startsWith("stage:")) {
            String stageId = target.substring(6);
            for (StageDefinition st : workflow.getStages()) {
                if (stageId.equalsIgnoreCase(st.getId()) && !st.getSteps().isEmpty()) {
                    return "step_" + sanitize(st.getSteps().get(0).getId());
                }
            }
        }
        if (target.startsWith("end:")) {
            return "end_" + sanitize(target.substring(4));
        }
        return defaultRef;
    }

    private String sanitize(String id) {
        return id != null ? id.replace("-", "_").replace(".", "_") : "node";
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
