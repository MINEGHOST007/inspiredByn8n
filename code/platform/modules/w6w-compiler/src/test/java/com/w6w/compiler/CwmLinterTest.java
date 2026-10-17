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
                      - id: wait-for-event
                        type: waitEvent
                """);

        CwmLinter.LintResult result = new CwmLinter().lint(workflow);

        assertThat(result.getIssues()).extracting(CwmLinter.LintIssue::getMessage)
                .anyMatch(message -> message.contains("waitEvent") && message.contains("not executable"));
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
}
