package com.w6w.governance;

import java.util.ArrayList;
import java.util.List;

public class EligibilityScorecard {
    public enum TierDecision {
        PASS,
        REVIEW,
        REJECT
    }

    private final String workflowName;
    private TierDecision decision = TierDecision.PASS;
    private final List<String> reasons = new ArrayList<>();

    public EligibilityScorecard(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getWorkflowName() { return workflowName; }
    public TierDecision getDecision() { return decision; }
    public List<String> getReasons() { return reasons; }

    public void upgradeTo(TierDecision newDecision, String reason) {
        reasons.add(reason);
        if (newDecision == TierDecision.REJECT) {
            this.decision = TierDecision.REJECT;
        } else if (newDecision == TierDecision.REVIEW && this.decision != TierDecision.REJECT) {
            this.decision = TierDecision.REVIEW;
        }
    }
}
