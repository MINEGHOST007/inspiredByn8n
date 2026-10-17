package com.w6w.runtime.actions;

/** Host extension point for sending email through the application's configured mail service. */
public interface WorkflowEmailGateway {
    void send(WorkflowEmailMessage message) throws Exception;
}
