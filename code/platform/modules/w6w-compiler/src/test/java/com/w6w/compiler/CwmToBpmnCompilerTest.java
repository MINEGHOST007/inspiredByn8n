package com.w6w.compiler;

import com.w6w.model.CwmParser;
import com.w6w.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CwmToBpmnCompilerTest {

    @Test
    void testDeterminismAndCompilation() throws Exception {
        File cwmFile = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        if (!cwmFile.exists()) {
            cwmFile = new File("../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        }
        assertThat(cwmFile).exists();

        WorkflowDefinition workflow = CwmParser.parse(cwmFile);
        assertThat(workflow).isNotNull();
        assertThat(workflow.getMetadata().getKey()).isEqualTo("refund-processing");

        CwmToBpmnCompiler compiler = new CwmToBpmnCompiler();

        // Compile twice to verify determinism
        String bpmnXml1 = compiler.compile(workflow);
        String bpmnXml2 = compiler.compile(workflow);

        assertThat(bpmnXml1).isNotEmpty();
        assertThat(bpmnXml1).isEqualTo(bpmnXml2); // Byte-identical determinism test

        // Structural assertions
        assertThat(bpmnXml1).contains("<process id=\"refund-processing\"");
        assertThat(bpmnXml1).contains("W6W CWM version: 1.0.0");
        assertThat(bpmnXml1).contains("<userTask id=\"step_receive_request\"");
        assertThat(bpmnXml1).contains("flowable:dueDate=\"${w6wSlaService.deadline('PT24H')}\"");
        assertThat(bpmnXml1).contains("<serviceTask id=\"step_validate_request_data\"");
        assertThat(bpmnXml1).contains("flowable:async=\"true\"")
                .contains("<flowable:failedJobRetryTimeCycle>R3/PT1M</flowable:failedJobRetryTimeCycle>");
        assertThat(bpmnXml1).contains("<exclusiveGateway id=\"gw_request_refund_approval\"");
        assertThat(bpmnXml1).contains("<endEvent id=\"step_close_refund_case\"");
        assertThat(bpmnXml1).contains("name=\"Resolution: Approved\"");
        assertThat(bpmnXml1).contains("<endEvent id=\"end_refund_denied\"");
    }

    @Test
    void unsupportedStepTypesFailInsteadOfBecomingUserTasks() throws Exception {
        File cwmFile = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        if (!cwmFile.exists()) cwmFile = new File("../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        WorkflowDefinition workflow = CwmParser.parse(cwmFile);
        workflow.getStages().get(0).getSteps().get(0).setType("newVendorStep");

        assertThatThrownBy(() -> new CwmToBpmnCompiler().compile(workflow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refusing to emit a user task as a fallback");
    }

    @Test
    void conditionCompilesToAnExplicitRunOrSkipGateway() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: conditional-workflow
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: conditional-review
                        name: Conditional review
                        type: userTask
                        assignee:
                          role: operations
                        condition:
                          field: data.needsReview
                          operator: eq
                          value: true
                      - id: finish
                        type: end
                """);

        String bpmn = new CwmToBpmnCompiler().compile(workflow);

        assertThat(bpmn).contains("targetRef=\"gw_condition_conditional_review\"")
                .contains("<exclusiveGateway id=\"gw_condition_conditional_review\"")
                .contains("default=\"flow_gw_condition_conditional_review_skip\"")
                .contains("${execution.getVariable('needsReview') == true}")
                .contains("targetRef=\"step_finish\"");
    }
}
