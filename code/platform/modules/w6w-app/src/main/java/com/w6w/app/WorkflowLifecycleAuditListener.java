package com.w6w.app;

import com.w6w.runtime.WorkflowLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Persists lifecycle events locally while leaving the same events available to host subscribers. */
@Component
public class WorkflowLifecycleAuditListener {
    private final AuditTrail audit;

    public WorkflowLifecycleAuditListener(AuditTrail audit) { this.audit = audit; }

    @EventListener
    public void onLifecycleEvent(WorkflowLifecycleEvent event) {
        audit.record(event.instanceId(), event.workflowKey(), event.businessKey(), event.taskId(), "system",
                event.eventType(), event.details());
    }
}
