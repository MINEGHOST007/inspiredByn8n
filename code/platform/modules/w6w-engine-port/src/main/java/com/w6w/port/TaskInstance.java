package com.w6w.port;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TaskInstance {
    private final String id;
    private final String instanceId;
    private final String name;
    private final String stepId;
    private final String assignee;
    private final List<String> candidateGroups;
    private final String formKey;
    private final int priority;
    private final Date dueDate;
    private final Map<String, Object> taskVariables;

    public TaskInstance(String id, String instanceId, String name, String stepId, String assignee, 
                        List<String> candidateGroups, String formKey, int priority, Date dueDate, Map<String, Object> taskVariables) {
        this.id = id;
        this.instanceId = instanceId;
        this.name = name;
        this.stepId = stepId;
        this.assignee = assignee;
        this.candidateGroups = candidateGroups != null ? candidateGroups : Collections.emptyList();
        this.formKey = formKey;
        this.priority = priority;
        this.dueDate = dueDate;
        this.taskVariables = taskVariables != null ? taskVariables : Collections.emptyMap();
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getName() { return name; }
    public String getStepId() { return stepId; }
    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return Collections.unmodifiableList(candidateGroups); }
    public String getFormKey() { return formKey; }
    public int getPriority() { return priority; }
    public Date getDueDate() { return dueDate; }
    public Map<String, Object> getTaskVariables() { return Collections.unmodifiableMap(taskVariables); }
}
