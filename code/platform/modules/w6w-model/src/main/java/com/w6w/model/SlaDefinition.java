package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public class SlaDefinition {
    private String goal;
    private String deadline;
    private String onGoal;
    private String onDeadline;

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }

    public String getOnGoal() { return onGoal; }
    public void setOnGoal(String onGoal) { this.onGoal = onGoal; }

    public String getOnDeadline() { return onDeadline; }
    public void setOnDeadline(String onDeadline) { this.onDeadline = onDeadline; }
}
