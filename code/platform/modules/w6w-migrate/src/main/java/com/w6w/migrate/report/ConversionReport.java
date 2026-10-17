package com.w6w.migrate.report;

import java.util.ArrayList;
import java.util.List;

/** A migration audit record. Unknown or inferred source behavior is never reported as ready. */
public class ConversionReport {
    private final String blueprintId;
    private final String caseTypeName;
    private int convertedCount;
    private int approximatedCount;
    private int unsupportedCount;
    private String eligibilityDecision = "NOT_ASSESSED";
    private final List<String> eligibilityReasons = new ArrayList<>();
    private final List<String> manualActionItems = new ArrayList<>();
    private final List<String> elementFindings = new ArrayList<>();

    public ConversionReport(String blueprintId, String caseTypeName) {
        this.blueprintId = blueprintId;
        this.caseTypeName = caseTypeName;
    }

    public void incrementConverted() { convertedCount++; }
    public void incrementApproximated() { approximatedCount++; }
    public void incrementUnsupported() { unsupportedCount++; }
    public void addActionItem(String item) { if (item != null && !item.isBlank()) manualActionItems.add(item); }
    public void recordElement(String elementId, String disposition, String confidence, String reason) {
        elementFindings.add("| `" + safe(elementId) + "` | " + safe(disposition) + " | "
                + safe(confidence) + " | " + safe(reason) + " |");
    }
    public void setEligibility(String decision, List<String> reasons) {
        eligibilityDecision = decision == null || decision.isBlank() ? "NOT_ASSESSED" : decision;
        eligibilityReasons.clear();
        if (reasons != null) eligibilityReasons.addAll(reasons);
    }

    public int getConvertedCount() { return convertedCount; }
    public int getApproximatedCount() { return approximatedCount; }
    public int getUnsupportedCount() { return unsupportedCount; }
    public String getEligibilityDecision() { return eligibilityDecision; }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# W6W Migration Report: ").append(caseTypeName).append("\n\n");
        sb.append("- **Source Blueprint ID:** `").append(blueprintId).append("`\n");
        sb.append("- **Tier 1 eligibility:** ").append(eligibilityDecision).append("\n");
        if ("REJECT".equalsIgnoreCase(eligibilityDecision)) {
            sb.append("- **Deployment status:** REJECTED — no workflow package generated\n");
        } else {
            sb.append("- **Deployment status:** DRAFT — complete manual actions and pass validation before deployment\n");
        }
        sb.append("- **Conversion coverage:** ").append(convertedCount).append(" converted, ")
                .append(approximatedCount).append(" requiring review, ")
                .append(unsupportedCount).append(" unsupported\n\n");

        if (!eligibilityReasons.isEmpty()) {
            sb.append("## Eligibility findings\n\n");
            for (String reason : eligibilityReasons) sb.append("- ").append(reason).append("\n");
            sb.append("\n");
        }
        sb.append("## Conversion findings\n\n");
        sb.append("| Source element | Disposition | Confidence | Finding |\n|---|---|---|---|\n");
        for (String finding : elementFindings) sb.append(finding).append("\n");
        if (elementFindings.isEmpty()) sb.append("| — | — | — | No elements were inspected |\n");
        sb.append("\n");
        sb.append("| Category | Count | Meaning |\n|---|---:|---|\n");
        sb.append("| Converted constructs | ").append(convertedCount).append(" | Mapped to the target model |\n");
        sb.append("| Requires review | ").append(approximatedCount).append(" | Draft mapping needs human confirmation |\n");
        sb.append("| Unsupported | ").append(unsupportedCount).append(" | Preserved in the report; not deployable |\n\n");
        sb.append("## Manual actions\n\n");
        if (manualActionItems.isEmpty()) sb.append("No actions recorded.\n");
        for (int i = 0; i < manualActionItems.size(); i++) {
            sb.append(i + 1).append(". [ ] ").append(manualActionItems.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String safe(String value) { return value == null ? "unknown" : value.replace("|", "\\|").replace("\n", " "); }
}
