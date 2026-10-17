package com.w6w.engine.flowable;

import com.w6w.runtime.WorkflowSlaEscalatedEvent;
import com.w6w.runtime.WorkflowMdcContext;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.identitylink.api.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Moves an overdue task from its current assignee/candidates to the configured escalation role. */
@Component("w6wSlaEscalationExecutor")
public class FlowableSlaEscalationExecutor implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(FlowableSlaEscalationExecutor.class);

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final ApplicationEventPublisher events;
    private Expression taskDefinitionKey;
    private Expression targetRole;

    public FlowableSlaEscalationExecutor(TaskService taskService, RuntimeService runtimeService,
                                         RepositoryService repositoryService, ApplicationEventPublisher events) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.events = events;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String taskKey = value(taskDefinitionKey, execution, "taskDefinitionKey");
        String role = value(targetRole, execution, "targetRole");
        if (taskKey.isBlank() || role.isBlank()) throw new IllegalStateException("SLA escalation requires the overdue task key and target role");
        Object traceValue = execution.getVariable(WorkflowMdcContext.TRACE_VARIABLE);
        String traceId = traceValue instanceof String value ? value : null;
        try (var ignored = WorkflowMdcContext.open(traceId, execution.getProcessInstanceId(), null, taskKey)) {
            escalate(execution, taskKey, role, traceId);
        }
    }

    private void escalate(DelegateExecution execution, String taskKey, String role, String traceId) {
        Task task = taskService.createTaskQuery().processInstanceId(execution.getProcessInstanceId())
                .taskDefinitionKey(taskKey).singleResult();
        if (task == null) {
            log.info("Skipping SLA escalation for task '{}' because it is no longer active", taskKey);
            return;
        }

        try (var ignored = WorkflowMdcContext.open(traceId, execution.getProcessInstanceId(), task.getId(), taskKey)) {
        if (task.getAssignee() != null) taskService.setAssignee(task.getId(), null);
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if ("candidate".equals(link.getType()) && link.getGroupId() != null)
                taskService.deleteCandidateGroup(task.getId(), link.getGroupId());
        }
        taskService.addCandidateGroup(task.getId(), role);

        var process = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
        ProcessDefinition definition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());
        WorkflowSlaEscalatedEvent event = new WorkflowSlaEscalatedEvent(task.getProcessInstanceId(),
                definition == null ? null : definition.getKey(), process == null ? null : process.getBusinessKey(),
                task.getId(), role, traceId, Instant.now());
        events.publishEvent(event);
        log.info("Escalated overdue task '{}' for instance '{}' to role '{}'", task.getId(), task.getProcessInstanceId(), role);
        }
    }

    private String value(Expression expression, DelegateExecution execution, String field) {
        return expression == null || expression.getValue(execution) == null ? "" : String.valueOf(expression.getValue(execution)).trim();
    }
}
