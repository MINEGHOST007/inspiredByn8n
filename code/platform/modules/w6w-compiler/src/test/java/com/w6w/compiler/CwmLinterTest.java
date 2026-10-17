package com.w6w.compiler;

import com.w6w.model.CwmParser;
import com.w6w.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;

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
}
