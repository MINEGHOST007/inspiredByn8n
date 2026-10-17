package com.w6w.migrate.model;

import java.util.ArrayList;
import java.util.List;

public class BlueprintIntermediateModel {
    private String blueprintId;
    private String applicationName;
    private final List<String> personas = new ArrayList<>();
    private final List<IntermediateCaseType> caseTypes = new ArrayList<>();

    public String getBlueprintId() { return blueprintId; }
    public void setBlueprintId(String blueprintId) { this.blueprintId = blueprintId; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public List<String> getPersonas() { return personas; }
    public List<IntermediateCaseType> getCaseTypes() { return caseTypes; }

    public static class IntermediateCaseType {
        private String name;
        private String description;
        private final List<IntermediateStage> stages = new ArrayList<>();
        private final List<IntermediateDataField> dataFields = new ArrayList<>();
        private final List<IntermediateBusinessRule> businessRules = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<IntermediateStage> getStages() { return stages; }
        public List<IntermediateDataField> getDataFields() { return dataFields; }
        public List<IntermediateBusinessRule> getBusinessRules() { return businessRules; }
    }

    public static class IntermediateStage {
        private String name;
        private boolean alternate = false;
        private final List<IntermediateStep> steps = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isAlternate() { return alternate; }
        public void setAlternate(boolean alternate) { this.alternate = alternate; }

        public List<IntermediateStep> getSteps() { return steps; }
    }

    public static class IntermediateStep {
        private String name;
        private String stepType; // collect, automation, approval, decision
        private String confidence = "medium";
        private String manualReviewReason;
        private String childWorkflowKey;

        public IntermediateStep(String name, String stepType) {
            this.name = name;
            this.stepType = stepType;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStepType() { return stepType; }
        public void setStepType(String stepType) { this.stepType = stepType; }

        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }

        public String getManualReviewReason() { return manualReviewReason; }
        public void setManualReviewReason(String manualReviewReason) { this.manualReviewReason = manualReviewReason; }

        public String getChildWorkflowKey() { return childWorkflowKey; }
        public void setChildWorkflowKey(String childWorkflowKey) { this.childWorkflowKey = childWorkflowKey; }
    }

    public static class IntermediateDataField {
        private String name;
        private String description;
        private String type;
        private boolean primary = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }
    }

    public static class IntermediateBusinessRule {
        private String name;
        private String description;

        public IntermediateBusinessRule(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
