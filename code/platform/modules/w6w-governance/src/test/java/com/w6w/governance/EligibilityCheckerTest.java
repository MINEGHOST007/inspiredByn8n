package com.w6w.governance;

import com.w6w.model.CwmParser;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityCheckerTest {
    private WorkflowDefinition refundWorkflow() throws Exception {
        File cwm = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        assertThat(cwm).exists();
        return CwmParser.parse(cwm);
    }

    @Test
    void refundProcessingIsEligibleForTierOne() throws Exception {
        EligibilityScorecard result = new EligibilityChecker().check(refundWorkflow());

        assertThat(result.getWorkflowName()).isEqualTo("Refund Processing");
        assertThat(result.getDecision()).isEqualTo(EligibilityScorecard.TierDecision.PASS);
    }

    @Test
    void crossDepartmentRoutingRequiresReview() throws Exception {
        WorkflowDefinition workflow = refundWorkflow();
        StepDefinition approval = workflow.getStages().stream()
                .flatMap(stage -> stage.getSteps().stream())
                .filter(step -> "request-refund-approval".equals(step.getId()))
                .findFirst().orElseThrow();
        approval.getAssignee().setRole("finance_operator");
        workflow.getStages().get(0).getSteps().get(0).getAssignee().setRole("property_manager");

        EligibilityScorecard result = new EligibilityChecker().check(workflow);

        assertThat(result.getDecision()).isEqualTo(EligibilityScorecard.TierDecision.REVIEW);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("distinct assignee roles"));
    }

    @Test
    void oneLevelChildWorkflowRequiresRuntimeReviewButIsNotRejectedByTierRubric() throws Exception {
        WorkflowDefinition workflow = refundWorkflow();
        workflow.getStages().get(0).getSteps().get(0).setChildWorkflowKey("nested-case");

        EligibilityScorecard result = new EligibilityChecker().check(workflow);

        assertThat(result.getDecision()).isEqualTo(EligibilityScorecard.TierDecision.REVIEW);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("one-level child workflow"));
    }

    @Test
    void childWorkflowDeeperThanOneLevelIsRejected() throws Exception {
        WorkflowDefinition workflow = refundWorkflow();
        StepDefinition child = workflow.getStages().get(0).getSteps().get(0);
        child.setChildWorkflowKey("nested-case");
        child.setChildWorkflowDepth(2);

        EligibilityScorecard result = new EligibilityChecker().check(workflow);

        assertThat(result.getDecision()).isEqualTo(EligibilityScorecard.TierDecision.REJECT);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("deeper than one level"));
    }
}
