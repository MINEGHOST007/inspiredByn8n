package com.w6w.test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioDefinition {
    private String name;
    private String workflowKey;
    private Map<String, Object> initialData = new HashMap<>();
    private List<ScenarioStep> steps = new ArrayList<>();
    private ScenarioAssertions assertions = new ScenarioAssertions();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public Map<String, Object> getInitialData() { return initialData; }
    public void setInitialData(Map<String, Object> initialData) { this.initialData = initialData; }

    public List<ScenarioStep> getSteps() { return steps; }
    public void setSteps(List<ScenarioStep> steps) { this.steps = steps; }

    public ScenarioAssertions getAssertions() { return assertions; }
    public void setAssertions(ScenarioAssertions assertions) { this.assertions = assertions; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioStep {
        private String action; // startWorkflow, completeTask, completeApproval
        private String stepId;
        private String user;
        private String outcome;
        private Map<String, Object> formData = new HashMap<>();

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getStepId() { return stepId; }
        public void setStepId(String stepId) { this.stepId = stepId; }

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }

        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }

        public Map<String, Object> getFormData() { return formData; }
        public void setFormData(Map<String, Object> formData) { this.formData = formData; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioAssertions {
        private String finalResolution;
        private List<String> completedStages = new ArrayList<>();

        public String getFinalResolution() { return finalResolution; }
        public void setFinalResolution(String finalResolution) { this.finalResolution = finalResolution; }

        public List<String> getCompletedStages() { return completedStages; }
        public void setCompletedStages(List<String> completedStages) { this.completedStages = completedStages; }
    }
}
