package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class StageDefinition {
    private String id;
    private String name;
    private boolean alternate = false;
    private String resolution;
    private List<StepDefinition> steps = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isAlternate() { return alternate; }
    public void setAlternate(boolean alternate) { this.alternate = alternate; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public List<StepDefinition> getSteps() { return steps; }
    public void setSteps(List<StepDefinition> steps) { this.steps = steps; }
}
