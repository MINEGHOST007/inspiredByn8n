package com.w6w.app;

import com.w6w.runtime.WorkflowSlaEscalatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Persists engine SLA escalations in the same audit feed as operator actions. */
@Component
public class WorkflowSlaAuditListener {
    private final AuditTrail audit;

    public WorkflowSlaAuditListener(AuditTrail audit) {
        this.audit = audit;
    }

    @EventListener
    public void onEscalated(WorkflowSlaEscalatedEvent event) {
        audit.record(event.instanceId(), event.workflowKey(), event.businessKey(), event.taskId(), "system",
                "SLA_DEADLINE_ESCALATED", Map.of("targetRole", event.targetRole(), "occurredAt", event.occurredAt().toString()));
    }
}
