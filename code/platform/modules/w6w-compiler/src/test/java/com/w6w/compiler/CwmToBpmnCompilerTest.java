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
        assertThat(bpmnXml1).contains("<userTask id=\"step_receive_request\"");
        assertThat(bpmnXml1).contains("<serviceTask id=\"step_validate_request_data\"");
        assertThat(bpmnXml1).contains("<exclusiveGateway id=\"gw_request_refund_approval\"");
        assertThat(bpmnXml1).contains("<endEvent id=\"step_close_refund_case\"");
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
}
