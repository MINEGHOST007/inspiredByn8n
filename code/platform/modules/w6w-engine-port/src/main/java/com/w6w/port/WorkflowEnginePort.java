package com.w6w.port;

import java.util.List;
import java.util.Map;

public interface WorkflowEnginePort {
    /**
     * Deploys a workflow definition (BPMN 2.0 XML) to the underlying engine.
     */
    String deploy(String key, String resourceName, String bpmnXml);

    /**
     * Starts a new workflow instance idempotently by business key.
     */
    WorkflowInstance startInstance(String workflowKey, String businessKey, Map<String, Object> variables);

    /**
     * Retrieves an active or historical instance by ID.
     */
    WorkflowInstance getInstance(String instanceId);

    /**
     * Retrieves active tasks assigned directly to a user or matching candidate groups.
     */
    List<TaskInstance> getTasksForUser(String username, List<String> userGroups);

    /**
     * Retrieves a task by task ID.
     */
    TaskInstance getTask(String taskId);

    /**
     * Claims a task for a user.
     */
    void claimTask(String taskId, String username);

    /**
     * Releases a claimed task back to candidate group pool.
     */
    void releaseTask(String taskId);

    /**
     * Completes a task with submission data and outcome.
     */
    void completeTask(String taskId, String username, Map<String, Object> variables);

    /**
     * Cancels an in-flight workflow instance with an audit reason.
     */
    void cancelInstance(String instanceId, String reason);

    /** Suspends an active workflow instance. */
    void suspendInstance(String instanceId);

    /** Resumes a suspended workflow instance. */
    void resumeInstance(String instanceId);

    /** Returns service-step jobs that exhausted their configured automatic retries. */
    List<WorkflowFailure> getDeadLetterJobs();

    /** Requeues a dead-letter job with a fresh bounded retry count. */
    void retryDeadLetterJob(String jobId, int retries);
}
