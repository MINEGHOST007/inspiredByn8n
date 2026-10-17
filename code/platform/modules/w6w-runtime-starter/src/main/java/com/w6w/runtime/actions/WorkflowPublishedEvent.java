package com.w6w.runtime.actions;

import java.time.Instant;
import java.util.Map;

/** Spring application event published for a workflow-defined domain event. */
public record WorkflowPublishedEvent(String eventName, String instanceId, String stepId,
                                     Map<String, Object> payload, Instant occurredAt) {}
