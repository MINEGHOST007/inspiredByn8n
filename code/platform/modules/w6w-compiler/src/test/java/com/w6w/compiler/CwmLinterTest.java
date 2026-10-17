package com.w6w.compiler;

import com.w6w.model.CwmParser;
import com.w6w.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CwmLinterTest {
    @Test
    void validRefundWorkflowHasNoLintErrors() throws Exception {
        File cwm = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        assertThat(cwm).exists();

        WorkflowDefinition workflow = CwmParser.parse(cwm);
        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void missingActionAndHumanAssigneeAreErrors() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: invalid-workflow
                  name: Invalid workflow
                stages:
                  - id: review
                    steps:
                      - id: human-review
                        type: userTask
                      - id: automation
                        type: serviceTask
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("has no assignee"))
                .anyMatch(message -> message.contains("has no registered action name"));
    }

    @Test
    void recognizedButUnimplementedStepTypesAreErrors() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: unsupported-workflow
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: unsupported-parallel
                        type: parallel
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("parallel") && message.contains("not executable"));
    }

    @Test
    void timerRequiresPositiveIso8601Duration() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: bad-timer
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: wait
                        type: timer
                        duration: PT0S
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("Timer step 'wait' duration must be greater than zero"));
    }

    @Test
    void slaEscalationRequiresDeadlineAndSupportedRoleAction() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: invalid-sla
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: review
                        type: userTask
                        assignee:
                          role: operations
                        sla:
                          onDeadline: auto-resolve
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("must use 'escalate:<role>'"))
                .anyMatch(message -> message.contains("requires a deadline duration"));
    }

    @Test
    void slaEscalationRoleMustBeDeclaredByTheWorkflow() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: undeclared-sla-role
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: review
                        type: userTask
                        assignee:
                          role: operations
                        sla:
                          deadline: PT1M
                          onDeadline: escalate:property_manager
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("must be declared in workflow roles"));
    }

    @Test
    void waitEventRequiresAValidEventName() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: invalid-event-name
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: wait-for-event
                        type: waitEvent
                        eventName: "bad event name"
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("eventName must start with a letter"));
    }

    @Test
    void invalidOutcomeTargetsAndUnreachableStepsAreErrors() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: unreachable-workflow
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                stages:
                  - id: intake
                    resolution: Done
                    steps:
                      - id: submit
                        type: userTask
                        assignee:
                          role: operations
                        outcomes:
                          finish: end:intake
                          route-badly: stage:missing-stage
                      - id: never-reached
                        type: serviceTask
                        action: work.process
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("references undefined stage 'missing-stage'"))
                .anyMatch(message -> message.contains("Step 'never-reached' is unreachable"));
    }

    @Test
    void conditionReferencesMustExistInTheWorkflowDataSchema(@TempDir Path packageDir) throws Exception {
        Files.createDirectories(packageDir.resolve("schemas"));
        Files.writeString(packageDir.resolve("schemas/data.schema.json"), """
                {"type":"object","properties":{"known":{"type":"string"}}}
                """);
        Path workflowFile = packageDir.resolve("workflow.cwm.yaml");
        Files.writeString(workflowFile, """
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: data-reference-workflow
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                data:
                  schema: schemas/data.schema.json
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: review
                        type: userTask
                        assignee:
                          role: operations
                        condition:
                          field: data.unknown
                          operator: exists
                """);

        WorkflowDefinition workflow = CwmParser.parse(workflowFile.toFile());
        CwmLinter.LintResult result = new CwmLinter().lint(workflow, workflowFile);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("references undefined data field 'data.unknown'"));
    }

    @Test
    void serviceTaskRetryPolicyRejectsInvalidBackoffAndFailureBehavior() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: invalid-retry-workflow
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: flaky-action
                        type: serviceTask
                        action: example.process
                        onError:
                          retries: 0
                          backoff: soon
                          onFailure: pretendSuccess
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("retries must be an integer"))
                .anyMatch(message -> message.contains("backoff must be an ISO-8601 duration"))
                .anyMatch(message -> message.contains("onFailure must be 'deadLetter'"));
    }

    @Test
    void sensitiveActionValuesMustReferenceHostSecrets() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: secure-action
                  version: 1.0.0
                  owner: platform
                  dataClassification: internal
                roles: []
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: request
                        type: serviceTask
                        action: w6w.http.request
                        config:
                          headers:
                            Authorization:
                              secretRef: PAYMENT_API_TOKEN
                      - id: finish
                        type: end
                """);
        CwmLinter linter = new CwmLinter();
        CwmLinter.LintResult valid = linter.lint(workflow);
        assertThat(valid.hasErrors()).withFailMessage("Expected secret reference to pass lint: " + valid.getIssues()).isFalse();

        workflow.getStages().get(0).getSteps().get(0).getConfig().put("accessToken", "do-not-store-this");
        CwmLinter.LintResult invalid = linter.lint(workflow);
        assertThat(invalid.hasErrors()).isTrue();
        assertThat(invalid.getIssues().toString()).contains("must use a host secret reference");
    }
}
