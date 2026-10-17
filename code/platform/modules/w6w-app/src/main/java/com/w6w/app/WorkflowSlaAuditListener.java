package com.w6w.app;

import com.w6w.runtime.WorkflowSlaEscalatedEvent;
import com.w6w.runtime.WorkflowLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Persists engine SLA escalations in the same audit feed as operator actions. */
@Component
public class WorkflowSlaAuditListener {
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;

    public WorkflowSlaAuditListener(AuditTrail audit, ApplicationEventPublisher events) {
        this.audit = audit;
        this.events = events;
    }

    @EventListener
    public void onEscalated(WorkflowSlaEscalatedEvent event) {
        audit.record(event.instanceId(), event.workflowKey(), event.businessKey(), event.taskId(), "system",
                "SLA_DEADLINE_ESCALATED", Map.of("targetRole", event.targetRole(), "occurredAt", event.occurredAt().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishLifecycleEvent(WorkflowSlaEscalatedEvent event) {
        events.publishEvent(new WorkflowLifecycleEvent("SLA_BREACHED", event.instanceId(), event.workflowKey(),
                event.businessKey(), event.taskId(), Map.of("targetRole", event.targetRole()), event.occurredAt()));
    }
}
