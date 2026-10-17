package com.w6w.governance;

import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EligibilityChecker evaluates a parsed WorkflowDefinition against the Tier 1
 * complexity rubric defined in SRS §GV-01 and the G5 gap analysis.
 *
 * <p>Decision ladder (first matching rule wins, ordered by severity):
 * <ol>
 *   <li>REJECT – hard constraints that make the workflow fundamentally
 *       incompatible with the W6W engine.</li>
 *   <li>REVIEW – soft constraints that need human assessment before migration
 *       can proceed.</li>
 *   <li>PASS  – the workflow is a clean Tier 1 candidate.</li>
 * </ol>
 *
 * <p>All thresholds are configurable via constructor; the zero-arg constructor
 * uses the SRS defaults.
 */
public class EligibilityChecker {

    // ── Thresholds (SRS GV-01 defaults) ──────────────────────────────────────
    private final int maxPrimaryStages;   // PASS if ≤ this many non-alternate stages
    private final int maxTotalSteps;      // PASS if ≤ this many total steps
    private final int maxRoles;           // PASS if ≤ this many distinct roles
    private final int maxParallelBranches;// REVIEW if a parallel step has > this many branches

    /** SRS defaults: ≤5 primary stages, ≤25 steps, ≤2 roles, ≤4 parallel branches. */
    public EligibilityChecker() {
        this(5, 25, 2, 4);
    }

    public EligibilityChecker(int maxPrimaryStages,
                              int maxTotalSteps,
                              int maxRoles,
                              int maxParallelBranches) {
        this.maxPrimaryStages    = maxPrimaryStages;
        this.maxTotalSteps       = maxTotalSteps;
        this.maxRoles            = maxRoles;
        this.maxParallelBranches = maxParallelBranches;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates the workflow and returns a fully populated scorecard.
     *
     * @param workflow a parsed, non-null WorkflowDefinition
     * @return scorecard with a PASS / REVIEW / REJECT decision and all reasons
     */
    public EligibilityScorecard check(WorkflowDefinition workflow) {
        String name = workflow.getMetadata() != null
                ? workflow.getMetadata().getName()
                : "(unnamed)";
        EligibilityScorecard scorecard = new EligibilityScorecard(name);

        List<StageDefinition> stages = workflow.getStages();

        if (workflow.getMetadata() == null || workflow.getMetadata().getDataClassification() == null
                || workflow.getMetadata().getDataClassification().isBlank()) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                    "REVIEW-V6: workflow has no data classification; owner must classify its data before deployment");
        }

        // ── REJECT rules ─────────────────────────────────────────────────────

        // R1: reject only hierarchy deeper than the Tier 1 one-child limit.
        boolean tooDeepChildWorkflow = stages.stream()
                .flatMap(s -> s.getSteps().stream())
                .anyMatch(step -> step.getChildWorkflowKey() != null
                        && !step.getChildWorkflowKey().isBlank()
                        && step.getChildWorkflowDepth() > 1);
        if (tooDeepChildWorkflow) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                    "REJECT-R1: workflow contains child-workflow hierarchy deeper than one level; keep in PEGA");
        } else {
            boolean hasChildWorkflow = stages.stream()
                    .flatMap(s -> s.getSteps().stream())
                    .anyMatch(step -> step.getChildWorkflowKey() != null && !step.getChildWorkflowKey().isBlank());
            if (hasChildWorkflow) {
                scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                        "REVIEW-R1: one-level child workflow is within the Tier 1 rubric, but callback execution must be verified before deployment");
            }
        }

        // R2: predictive-model or AI-decision steps
        boolean hasPredictiveModel = stages.stream()
                .flatMap(s -> s.getSteps().stream())
                .anyMatch(step -> "predictiveModel".equalsIgnoreCase(step.getType())
                        || "aiDecision".equalsIgnoreCase(step.getType()));
        if (hasPredictiveModel) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                    "REJECT-R2: workflow uses predictive-model or AI-decision steps; " +
                    "keep in PEGA");
        }

        // R3: ad-hoc / unstructured case steps
        boolean hasAdHoc = stages.stream()
                .flatMap(s -> s.getSteps().stream())
                .anyMatch(step -> "adHoc".equalsIgnoreCase(step.getType())
                        || "caseCreate".equalsIgnoreCase(step.getType()));
        if (hasAdHoc) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                    "REJECT-R3: workflow contains ad-hoc or case-create steps; " +
                    "these require PEGA case management");
        }

        boolean hasUnknownType = stages.stream().flatMap(s -> s.getSteps().stream())
                .anyMatch(step -> step.getType() == null || !Set.of("userTask", "approval", "serviceTask", "decision", "parallel",
                        "subStage", "waitEvent", "timer", "childWorkflow", "end")
                        .contains(step.getType()));
        if (hasUnknownType) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                    "REJECT-R4: workflow contains an unknown step type; resolve it in the source system or keep it in PEGA");
        }

        // ── REVIEW rules ─────────────────────────────────────────────────────

        // V1: spans > maxRoles distinct assignee roles (cross-departmental routing)
        Set<String> assigneeRoles = new HashSet<>();
        for (StageDefinition stage : stages) {
            for (StepDefinition step : stage.getSteps()) {
                if (step.getAssignee() != null && step.getAssignee().getRole() != null) {
                    assigneeRoles.add(step.getAssignee().getRole());
                }
            }
        }
        if (assigneeRoles.size() > maxRoles) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                    "REVIEW-V1: workflow routes to " + assigneeRoles.size() +
                    " distinct assignee roles " + assigneeRoles +
                    " (threshold " + maxRoles + "); cross-departmental routing needs review");
        }

        // V2: parallel steps with more than maxParallelBranches branches
        for (StageDefinition stage : stages) {
            for (StepDefinition step : stage.getSteps()) {
                if ("parallel".equalsIgnoreCase(step.getType())
                        && step.getOutcomes() != null
                        && step.getOutcomes().size() > maxParallelBranches) {
                    scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                            "REVIEW-V2: step '" + step.getId() + "' in stage '" +
                            stage.getId() + "' has " + step.getOutcomes().size() +
                            " parallel branches (threshold " + maxParallelBranches + ")");
                }
            }
        }

        // V3: manual-condition flags – extractor marks conditions it could not
        //     deterministically parse as "NEEDS_MANUAL_CONDITION"
        boolean hasManualCondition = stages.stream()
                .flatMap(s -> s.getSteps().stream())
                .anyMatch(step -> step.getCondition() != null
                        && "NEEDS_MANUAL_CONDITION".equals(step.getCondition().getOperator()));
        if (hasManualCondition) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                    "REVIEW-V3: one or more steps contain conditions flagged as " +
                    "'NEEDS_MANUAL_CONDITION' by the PDF extractor; " +
                    "a human must author the final condition expression");
        }

        // ── Structural PASS thresholds ────────────────────────────────────────

        long primaryStageCount = stages.stream()
                .filter(s -> !s.isAlternate())
                .count();
        if (primaryStageCount > maxPrimaryStages) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                    "REVIEW-V4: workflow has " + primaryStageCount +
                    " primary (non-alternate) stages (threshold " + maxPrimaryStages + "); " +
                    "consider splitting into multiple Tier 1 workflows");
        }

        long totalSteps = stages.stream()
                .mapToLong(s -> s.getSteps().size())
                .sum();
        if (totalSteps > maxTotalSteps) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.REVIEW,
                    "REVIEW-V5: workflow has " + totalSteps +
                    " total steps (threshold " + maxTotalSteps + ")");
        }

        // If scorecard is still PASS, record the positive confirmation
        if (scorecard.getDecision() == EligibilityScorecard.TierDecision.PASS) {
            scorecard.upgradeTo(EligibilityScorecard.TierDecision.PASS,
                    "PASS: " + primaryStageCount + " primary stage(s), " +
                    totalSteps + " total step(s), " +
                    assigneeRoles.size() + " assignee role(s) — all within Tier 1 thresholds");
        }

        return scorecard;
    }
}
