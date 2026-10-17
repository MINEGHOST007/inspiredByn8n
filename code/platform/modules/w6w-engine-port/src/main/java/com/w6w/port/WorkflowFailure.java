package com.w6w.port;

import java.time.Instant;

/** Engine-neutral summary of a service step that exhausted its automatic retries. */
public record WorkflowFailure(
        String id,
        String instanceId,
        String workflowKey,
        String businessKey,
        String stepId,
        String stepName,
        String errorMessage,
        Instant failedAt) {
}
