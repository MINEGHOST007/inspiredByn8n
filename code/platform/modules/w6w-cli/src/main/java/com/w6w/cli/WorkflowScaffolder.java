package com.w6w.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.w6w.compiler.CwmLinter;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class WorkflowScaffolder {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    Path create(Path root, String key, String name, String owner, String domain, String template) throws IOException {
        if (!key.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Workflow key must be lowercase kebab-case");
        if (!template.matches("approval|intake-and-fulfil|multi-level-approval|request-with-sla"))
            throw new IllegalArgumentException("Unknown template '" + template + "'. Choose approval, intake-and-fulfil, multi-level-approval, or request-with-sla.");
        Path packageDir = root.resolve("code/workflows/domains").resolve(domain).resolve(key).normalize();
        if (!packageDir.startsWith(root.resolve("code/workflows/domains").normalize()))
            throw new IllegalArgumentException("Domain path must stay under code/workflows/domains");
        if (Files.exists(packageDir)) throw new IllegalArgumentException("Workflow folder already exists: " + packageDir);

        WorkflowDefinition workflow = definition(key, name, owner, template);
        CwmLinter.LintResult lint = new CwmLinter().lint(workflow);
        if (lint.hasErrors()) throw new IllegalStateException("Built-in template failed validation: " + lint.getIssues());
        new CwmToBpmnCompiler().compile(workflow);

        Files.createDirectories(packageDir.resolve("schemas"));
        Files.createDirectories(packageDir.resolve("forms"));
        Files.createDirectories(packageDir.resolve("tests/scenarios"));
        YAML.writeValue(packageDir.resolve("workflow.cwm.yaml").toFile(), workflow);
        JSON.writerWithDefaultPrettyPrinter().writeValue(packageDir.resolve("schemas/data.schema.json").toFile(), dataSchema());
        JSON.writerWithDefaultPrettyPrinter().writeValue(packageDir.resolve("forms/request.json").toFile(), formSchema(name));
        Files.writeString(packageDir.resolve("tests/scenarios/happy-path.yaml"), scenario(key, template));
        Files.writeString(packageDir.resolve("README.md"), "# " + name + "\n\n"
                + "Scaffolded from the `" + template + "` template. Review roles, fields, outcomes, owner, and data classification before use.\n");
        return packageDir;
    }

    private WorkflowDefinition definition(String key, String name, String owner, String template) {
        WorkflowMetadata metadata = new WorkflowMetadata();
        metadata.setKey(key); metadata.setName(name); metadata.setOwner(owner); metadata.setVersion("0.1.0");
        metadata.setTier(1); metadata.setDataClassification("internal"); metadata.setSource("scaffold:" + template);
        metadata.setDescription("A starter " + template + " workflow. Review and adapt before deployment.");

        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setApiVersion("w6w/v1"); workflow.setKind("Workflow"); workflow.setMetadata(metadata);
        workflow.setRoles(List.of("requester", "approver", "senior_approver", "fulfillment"));
        workflow.setData(Map.of("schema", "schemas/data.schema.json"));

        StageDefinition intake = stage("request-intake", "Request intake");
        intake.getSteps().add(humanStep("collect-request", "Collect request", "userTask", "requester", null));
        workflow.getStages().add(intake);

        switch (template) {
            case "intake-and-fulfil" -> {
                StageDefinition fulfill = stage("fulfillment", "Fulfillment");
                fulfill.getSteps().add(humanStep("fulfill-request", "Fulfill request", "userTask", "fulfillment", null));
                workflow.getStages().add(fulfill);
                workflow.getStages().add(terminal("complete", "Complete", "Completed"));
            }
            case "multi-level-approval" -> {
                workflow.getStages().add(approvalStage("supervisor-review", "Supervisor review", "supervisor_approval", "Approve as supervisor", "approver", null));
                workflow.getStages().add(approvalStage("senior-review", "Senior review", "senior_approval", "Approve as senior approver", "senior_approver", null));
                addApprovalTerminals(workflow);
            }
            case "request-with-sla" -> {
                workflow.getStages().add(approvalStage("approval", "Approval", "manager_approval", "Review request", "approver", "PT24H"));
                addApprovalTerminals(workflow);
            }
            default -> {
                workflow.getStages().add(approvalStage("approval", "Approval", "manager_approval", "Review request", "approver", null));
                addApprovalTerminals(workflow);
            }
        }
        return workflow;
    }

    private void addApprovalTerminals(WorkflowDefinition workflow) {
        workflow.getStages().add(terminal("approved", "Approved", "Approved"));
        StageDefinition rejected = terminal("rejected", "Rejected", "Rejected");
        rejected.setAlternate(true);
        workflow.getStages().add(rejected);
    }

    private StageDefinition approvalStage(String stageId, String stageName, String stepId, String stepName,
                                          String role, String deadline) {
        StageDefinition stage = stage(stageId, stageName);
        StepDefinition approval = humanStep(stepId, stepName, "approval", role, null);
        Map<String, String> outcomes = new LinkedHashMap<>();
        outcomes.put("approve", "next"); outcomes.put("reject", "stage:rejected");
        approval.setOutcomes(outcomes);
        if (deadline != null) {
            SlaDefinition sla = new SlaDefinition(); sla.setGoal("PT4H"); sla.setDeadline(deadline); sla.setOnDeadline("escalate:senior_approver");
            approval.setSla(sla);
        }
        stage.getSteps().add(approval);
        return stage;
    }

    private StepDefinition humanStep(String id, String name, String type, String role, Map<String, String> outcomes) {
        StepDefinition step = new StepDefinition(); step.setId(id); step.setName(name); step.setType(type);
        step.setForm("forms/request.json");
        AssigneeDefinition assignee = new AssigneeDefinition(); assignee.setRole(role); step.setAssignee(assignee);
        if (outcomes != null) step.setOutcomes(outcomes);
        return step;
    }

    private StageDefinition terminal(String id, String name, String resolution) {
        StageDefinition stage = stage(id, name); stage.setResolution(resolution);
        StepDefinition end = new StepDefinition(); end.setId("close-" + id); end.setName("Close " + name); end.setType("end");
        stage.getSteps().add(end); return stage;
    }

    private StageDefinition stage(String id, String name) {
        StageDefinition stage = new StageDefinition(); stage.setId(id); stage.setName(name); return stage;
    }

    private Map<String, Object> dataSchema() {
        return Map.of("$schema", "https://json-schema.org/draft/2020-12/schema", "type", "object",
                "properties", Map.of("requesterName", Map.of("type", "string"), "requestReason", Map.of("type", "string")),
                "required", List.of("requesterName", "requestReason"));
    }

    private Map<String, Object> formSchema(String name) {
        return Map.of("title", name + " request", "type", "object", "required", List.of("requesterName", "requestReason"),
                "properties", Map.of("requesterName", Map.of("type", "string", "title", "Your name"),
                        "requestReason", Map.of("type", "string", "title", "Request details", "minLength", 5)));
    }

    private String scenario(String key, String template) {
        String resolution = "intake-and-fulfil".equals(template) ? "Completed" : "Approved";
        StringBuilder yaml = new StringBuilder("name: \"Generated happy path\"\nworkflowKey: \"")
                .append(key).append("\"\ninitialData:\n  requesterName: \"Taylor Example\"\n  requestReason: \"Sample request for local testing\"\nsteps:\n")
                .append("  - action: startWorkflow\n")
                .append(step("collect-request", "requester", null));
        if ("intake-and-fulfil".equals(template)) {
            yaml.append(step("fulfill-request", "fulfillment", null));
        } else if ("multi-level-approval".equals(template)) {
            yaml.append(step("supervisor_approval", "approver", "approve"));
            yaml.append(step("senior_approval", "senior_approver", "approve"));
        } else {
            yaml.append(step("manager_approval", "approver", "approve"));
        }
        return yaml.append("assertions:\n  finalResolution: \"").append(resolution).append("\"\n").toString();
    }

    private String step(String stepId, String user, String outcome) {
        StringBuilder yaml = new StringBuilder("  - action: completeTask\n    stepId: \"")
                .append(stepId).append("\"\n    user: \"").append(user).append("\"\n");
        if (outcome != null) yaml.append("    outcome: \"").append(outcome).append("\"\n");
        return yaml.append("    formData:\n      requesterName: \"Taylor Example\"\n      requestReason: \"Sample request for local testing\"\n").toString();
    }
}
