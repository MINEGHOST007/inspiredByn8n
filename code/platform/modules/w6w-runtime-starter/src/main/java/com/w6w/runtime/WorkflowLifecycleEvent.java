package com.w6w.runtime;

import java.time.Instant;
import java.util.Map;

/** Host-facing workflow lifecycle notification. Details intentionally exclude process variables. */
public record WorkflowLifecycleEvent(String eventType, String instanceId, String workflowKey,
                                     String businessKey, String taskId, Map<String, Object> details,
                                     Instant occurredAt) {
    public WorkflowLifecycleEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
