package com.w6w.port.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ActionContext {
    private final String instanceId;
    private final String stepId;
    private final String idempotencyKey;
    private final Map<String, Object> variables;

    public ActionContext(String instanceId, String stepId, Map<String, Object> variables) {
        this.instanceId = instanceId;
        this.stepId = stepId;
        this.idempotencyKey = (instanceId != null && stepId != null) ? (instanceId + ":" + stepId) : "";
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
    }

    public String getInstanceId() { return instanceId; }
    public String getStepId() { return stepId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String name, Class<T> clazz) {
        Object val = variables.get(name);
        if (val != null && clazz.isAssignableFrom(val.getClass())) {
            return (T) val;
        }
        return null;
    }
}
