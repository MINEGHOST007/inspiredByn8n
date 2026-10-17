package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public class AssigneeDefinition {
    private String role;
    private String user;
    private String lookup;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getLookup() { return lookup; }
    public void setLookup(String lookup) { this.lookup = lookup; }
}
