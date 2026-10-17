package com.w6w.port.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ActionResult {
    private final boolean success;
    private final Map<String, Object> outputVariables;
    private final String errorMessage;

    private ActionResult(boolean success, Map<String, Object> outputVariables, String errorMessage) {
        this.success = success;
        this.outputVariables = outputVariables != null ? new HashMap<>(outputVariables) : Collections.emptyMap();
        this.errorMessage = errorMessage;
    }

    public static ActionResult success() {
        return new ActionResult(true, Collections.emptyMap(), null);
    }

    public static ActionResult success(Map<String, Object> outputVariables) {
        return new ActionResult(true, outputVariables, null);
    }

    public static ActionResult failure(String errorMessage) {
        return new ActionResult(false, Collections.emptyMap(), errorMessage);
    }

    public boolean isSuccess() { return success; }
    public Map<String, Object> getOutputVariables() { return outputVariables; }
    public String getErrorMessage() { return errorMessage; }
}
