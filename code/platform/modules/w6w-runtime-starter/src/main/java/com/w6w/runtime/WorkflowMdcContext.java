package com.w6w.runtime;

import org.slf4j.MDC;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scoped MDC values shared by HTTP requests and asynchronous workflow execution. */
public final class WorkflowMdcContext {
    public static final String TRACE_VARIABLE = "__w6wTraceId";
    private static final String[] KEYS = {"traceId", "instanceId", "taskId", "stepId"};

    private WorkflowMdcContext() { }

    public static Scope open(String traceId, String instanceId, String taskId, String stepId) {
        Map<String, String> previous = new LinkedHashMap<>();
        for (String key : KEYS) {
            previous.put(key, MDC.get(key));
        }
        set("traceId", traceId);
        set("instanceId", instanceId);
        set("taskId", taskId);
        set("stepId", stepId);
        return () -> {
            for (String key : KEYS) set(key, previous.get(key));
        };
    }

    public static String currentTraceId() { return MDC.get("traceId"); }

    public static Map<String, Object> workflowData(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return Collections.emptyMap();
        Map<String, Object> workflowData = new LinkedHashMap<>(variables);
        workflowData.remove(TRACE_VARIABLE);
        return workflowData;
    }

    private static void set(String key, String value) {
        if (value == null || value.isBlank()) MDC.remove(key);
        else MDC.put(key, value);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
