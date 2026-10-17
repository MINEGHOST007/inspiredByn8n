package com.w6w.port;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WorkflowInstance {
    private final String id;
    private final String workflowKey;
    private final String businessKey;
    private final String currentStage;
    private final String status; // RUNNING, COMPLETED, SUSPENDED, TERMINATED
    private final String resolution;
    private final Map<String, Object> variables;

    public WorkflowInstance(String id, String workflowKey, String businessKey, String currentStage, String status, String resolution, Map<String, Object> variables) {
        this.id = id;
        this.workflowKey = workflowKey;
        this.businessKey = businessKey;
        this.currentStage = currentStage;
        this.status = status;
        this.resolution = resolution;
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
    }

    public String getId() { return id; }
    public String getWorkflowKey() { return workflowKey; }
    public String getBusinessKey() { return businessKey; }
    public String getCurrentStage() { return currentStage; }
    public String getStatus() { return status; }
    public String getResolution() { return resolution; }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }
}
