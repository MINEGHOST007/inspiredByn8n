package com.w6w.runtime;

import java.time.Instant;

/** Published when a task deadline moves an overdue task into an escalation role's queue. */
public record WorkflowSlaEscalatedEvent(String instanceId, String workflowKey, String businessKey,
                                        String taskId, String targetRole, Instant occurredAt) { }
