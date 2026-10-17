package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public class WorkflowDefinition {
    private String apiVersion;
    private String kind;
    private WorkflowMetadata metadata;
    private Map<String, Object> data;
    private List<String> roles = new ArrayList<>();
    private List<StageDefinition> stages = new ArrayList<>();

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public WorkflowMetadata getMetadata() { return metadata; }
    public void setMetadata(WorkflowMetadata metadata) { this.metadata = metadata; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public List<StageDefinition> getStages() { return stages; }
    public void setStages(List<StageDefinition> stages) { this.stages = stages; }
}
