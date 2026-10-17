package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public class StepDefinition {
    private String id;
    private String name;
    private String type; // userTask, approval, serviceTask, decision, parallel, waitEvent, timer, end
    private AssigneeDefinition assignee;
    private String form;
    private String action;
    private String duration;
    private String eventName;
    private String childWorkflowKey;
    private int childWorkflowDepth;
    private String subStage;
    private String decision;
    private SlaDefinition sla;
    private ConditionDefinition condition;
    private Map<String, String> outcomes;
    private Map<String, Object> onError;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public AssigneeDefinition getAssignee() { return assignee; }
    public void setAssignee(AssigneeDefinition assignee) { this.assignee = assignee; }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getChildWorkflowKey() { return childWorkflowKey; }
    public void setChildWorkflowKey(String childWorkflowKey) { this.childWorkflowKey = childWorkflowKey; }

    public int getChildWorkflowDepth() { return childWorkflowDepth; }
    public void setChildWorkflowDepth(int childWorkflowDepth) { this.childWorkflowDepth = childWorkflowDepth; }

    public String getSubStage() { return subStage; }
    public void setSubStage(String subStage) { this.subStage = subStage; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public SlaDefinition getSla() { return sla; }
    public void setSla(SlaDefinition sla) { this.sla = sla; }

    public ConditionDefinition getCondition() { return condition; }
    public void setCondition(ConditionDefinition condition) { this.condition = condition; }

    public Map<String, String> getOutcomes() { return outcomes; }
    public void setOutcomes(Map<String, String> outcomes) { this.outcomes = outcomes; }

    public Map<String, Object> getOnError() { return onError; }
    public void setOnError(Map<String, Object> onError) { this.onError = onError; }
}
