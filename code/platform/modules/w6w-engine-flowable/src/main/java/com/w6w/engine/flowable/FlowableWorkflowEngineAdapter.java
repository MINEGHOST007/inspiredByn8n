package com.w6w.engine.flowable;

import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowEnginePort;
import com.w6w.port.WorkflowFailure;
import com.w6w.port.WorkflowInstance;
import com.w6w.runtime.WorkflowDataSchemaRegistry;
import org.flowable.engine.ManagementService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FlowableWorkflowEngineAdapter implements WorkflowEnginePort {
    private static final Logger log = LoggerFactory.getLogger(FlowableWorkflowEngineAdapter.class);

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ManagementService managementService;
    private final WorkflowDataSchemaRegistry dataSchemas;

    @Autowired
    public FlowableWorkflowEngineAdapter(RepositoryService repositoryService,
                                         RuntimeService runtimeService,
                                         TaskService taskService,
                                         HistoryService historyService,
                                         ManagementService managementService,
                                         WorkflowDataSchemaRegistry dataSchemas) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.managementService = managementService;
        this.dataSchemas = dataSchemas;
    }

    public FlowableWorkflowEngineAdapter(RepositoryService repositoryService,
                                         RuntimeService runtimeService,
                                         TaskService taskService,
                                         HistoryService historyService,
                                         ManagementService managementService) {
        this(repositoryService, runtimeService, taskService, historyService, managementService, null);
    }

    @Override
    public String deploy(String key, String resourceName, String bpmnXml) {
        Deployment deployment = repositoryService.createDeployment()
            .key(key)
            .name(resourceName)
            .addString(resourceName + ".bpmn20.xml", bpmnXml)
            .deploy();
        log.info("Deployed workflow '{}' with Flowable deployment ID: {}", key, deployment.getId());
        return deployment.getId();
    }

    @Override
    public WorkflowInstance startInstance(String workflowKey, String businessKey, Map<String, Object> variables) {
        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("A non-blank business key is required for idempotent workflow start");
        }
        ProcessInstance existing = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey(workflowKey)
            .processInstanceBusinessKey(businessKey)
            .singleResult();

        if (existing != null) {
            log.warn("Workflow instance with business key '{}' already exists: {}", businessKey, existing.getId());
            return mapActiveInstance(existing);
        }

        HistoricProcessInstance completed = historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey(workflowKey)
            .processInstanceBusinessKey(businessKey)
            .includeProcessVariables()
            .finished()
            .singleResult();
        if (completed != null) {
            log.warn("Workflow instance with completed business key '{}' already exists: {}", businessKey, completed.getId());
            return mapHistoricInstance(completed);
        }

        if (dataSchemas != null) {
            var definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(workflowKey).latestVersion().singleResult();
            if (definition != null) dataSchemas.validate(definition.getId(), variables);
        }

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(workflowKey, businessKey, variables);
        log.info("Started instance '{}' for workflow '{}' (BusinessKey: '{}')", pi.getId(), workflowKey, businessKey);
        return mapActiveInstance(pi);
    }

    @Override
    public WorkflowInstance getInstance(String instanceId) {
        ProcessInstance active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instanceId)
            .includeProcessVariables()
            .singleResult();

        if (active != null) {
            return mapActiveInstance(active);
        }

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instanceId)
            .includeProcessVariables()
            .singleResult();

        if (historic != null) {
            return mapHistoricInstance(historic);
        }

        return null;
    }

    @Override
    public List<TaskInstance> getTasksForUser(String username, List<String> userGroups) {
        TaskQuery query = taskService.createTaskQuery();
        if (username != null && (userGroups == null || userGroups.isEmpty())) {
            query.taskAssignee(username);
        } else if (username != null) {
            query.or()
                .taskAssignee(username)
                .taskCandidateGroupIn(userGroups)
                .endOr();
        } else if (userGroups != null && !userGroups.isEmpty()) {
            query.taskCandidateGroupIn(userGroups);
        }

        List<Task> tasks = query.includeTaskLocalVariables().includeProcessVariables().list();
        return tasks.stream().map(this::mapTask).collect(Collectors.toList());
    }

    @Override
    public TaskInstance getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).includeProcessVariables().singleResult();
        return task != null ? mapTask(task) : null;
    }

    @Override
    public void claimTask(String taskId, String username) {
        taskService.claim(taskId, username);
        log.info("Claimed task '{}' for user '{}'", taskId, username);
    }

    @Override
    public void releaseTask(String taskId) {
        taskService.unclaim(taskId);
        log.info("Released task '{}'", taskId);
    }

    @Override
    public void completeTask(String taskId, String username, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found with ID: " + taskId);
        }

        if (dataSchemas != null) {
            Map<String, Object> mergedVariables = new HashMap<>(runtimeService.getVariables(task.getProcessInstanceId()));
            if (variables != null) mergedVariables.putAll(variables);
            dataSchemas.validate(task.getProcessDefinitionId(), mergedVariables);
        }

        if (task.getAssignee() == null && username != null) {
            taskService.claim(taskId, username);
        }

        taskService.complete(taskId, variables);
        log.info("Completed task '{}' (User: '{}')", taskId, username);
    }

    @Override
    public void cancelInstance(String instanceId, String reason) {
        runtimeService.deleteProcessInstance(instanceId, reason);
        log.info("Cancelled workflow instance '{}' (Reason: '{}')", instanceId, reason);
    }

    @Override
    public void suspendInstance(String instanceId) {
        runtimeService.suspendProcessInstanceById(instanceId);
        log.info("Suspended workflow instance '{}'", instanceId);
    }

    @Override
    public void resumeInstance(String instanceId) {
        runtimeService.activateProcessInstanceById(instanceId);
        log.info("Resumed workflow instance '{}'", instanceId);
    }

    @Override
    public List<WorkflowFailure> getDeadLetterJobs() {
        return managementService.createDeadLetterJobQuery().orderByJobCreateTime().desc().list().stream()
                .map(job -> {
                    WorkflowInstance instance = job.getProcessInstanceId() == null ? null : getInstance(job.getProcessInstanceId());
                    String elementId = job.getElementId();
                    String stepId = elementId != null && elementId.startsWith("step_")
                            ? elementId.substring(5).replace('_', '-') : elementId;
                    return new WorkflowFailure(job.getId(), job.getProcessInstanceId(),
                            instance == null ? null : instance.getWorkflowKey(),
                            instance == null ? null : instance.getBusinessKey(),
                            stepId, job.getElementName(), bounded(job.getExceptionMessage()),
                            job.getCreateTime() == null ? null : job.getCreateTime().toInstant());
                }).toList();
    }

    @Override
    public void retryDeadLetterJob(String jobId, int retries) {
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("A dead-letter job ID is required");
        if (retries < 1 || retries > 100) throw new IllegalArgumentException("Retry count must be between 1 and 100");
        Job job = managementService.createDeadLetterJobQuery().jobId(jobId).singleResult();
        if (job == null) throw new NoSuchElementException("Dead-letter job not found: " + jobId);
        managementService.moveDeadLetterJobToExecutableJob(jobId, retries);
        log.info("Requeued dead-letter job '{}' for instance '{}' with {} retries", jobId, job.getProcessInstanceId(), retries);
    }

    private String bounded(String message) {
        if (message == null) return null;
        return message.length() <= 1000 ? message : message.substring(0, 1000) + "…";
    }

    private WorkflowInstance mapActiveInstance(ProcessInstance pi) {
        Map<String, Object> vars = pi.getProcessVariables() != null ? pi.getProcessVariables() : Collections.emptyMap();
        return new WorkflowInstance(
            pi.getId(),
            pi.getProcessDefinitionKey(),
            pi.getBusinessKey(),
            pi.getActivityId(),
            pi.isSuspended() ? "SUSPENDED" : "RUNNING",
            null,
            vars
        );
    }

    private WorkflowInstance mapHistoricInstance(HistoricProcessInstance hpi) {
        Map<String, Object> vars = hpi.getProcessVariables() != null ? hpi.getProcessVariables() : Collections.emptyMap();
        String resolution = (String) vars.get("resolution");
        if (resolution == null && hpi.getEndActivityId() != null) {
            var endActivity = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(hpi.getId())
                    .activityId(hpi.getEndActivityId())
                    .singleResult();
            if (endActivity != null && endActivity.getActivityName() != null
                    && endActivity.getActivityName().startsWith("Resolution: ")) {
                resolution = endActivity.getActivityName().substring("Resolution: ".length());
            }
        }
        return new WorkflowInstance(
            hpi.getId(),
            hpi.getProcessDefinitionKey(),
            hpi.getBusinessKey(),
            hpi.getEndActivityId(),
            hpi.getDeleteReason() != null ? "CANCELLED" : "COMPLETED",
            resolution != null ? resolution : (hpi.getDeleteReason() != null ? "CANCELLED" : "COMPLETED"),
            vars
        );
    }

    private TaskInstance mapTask(Task t) {
        return new TaskInstance(
            t.getId(),
            t.getProcessInstanceId(),
            t.getName(),
            t.getTaskDefinitionKey(),
            t.getAssignee(),
            Collections.emptyList(),
            t.getFormKey(),
            t.getPriority(),
            t.getDueDate(),
            t.getProcessVariables()
        );
    }
}
