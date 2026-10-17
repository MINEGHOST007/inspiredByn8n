package com.w6w.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.w6w.engine.flowable.FlowableWorkflowEngineAdapter;
import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowInstance;
import com.w6w.port.WorkflowFailure;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class WorkflowApiController {
    private final WorkflowCatalog catalog;
    private final FlowableWorkflowEngineAdapter engine;
    private final ProcessEngine processEngine;
    private final AuditTrail audit;

    public WorkflowApiController(WorkflowCatalog catalog, FlowableWorkflowEngineAdapter engine, ProcessEngine processEngine, AuditTrail audit) {
        this.catalog = catalog; this.engine = engine; this.processEngine = processEngine; this.audit = audit;
    }

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "UP", "mode", "local-demo", "workflows", catalog.list().size()); }

    @GetMapping("/workflows")
    public List<Map<String, Object>> workflows() { return catalog.list(); }

    @GetMapping("/workflows/{key}")
    public Map<String, Object> workflow(@PathVariable("key") String key) { return catalog.summary(catalog.require(key)); }

    @PostMapping("/admin/workflows/reload")
    public Map<String, Object> reloadWorkflow(@RequestParam(name = "key") String key) throws Exception {
        WorkflowCatalog.CatalogEntry entry = catalog.reload(key);
        ProcessDefinition deployed = processEngine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey(key).orderByProcessDefinitionVersion().desc().listPage(0, 1).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow did not deploy: " + key));
        return Map.of("workflowKey", key, "semanticVersion", entry.definition().getMetadata().getVersion(),
                "engineVersion", deployed.getVersion(), "status", "reloaded");
    }

    @PostMapping("/instances")
    public ResponseEntity<WorkflowInstance> start(@RequestBody StartRequest request) {
        WorkflowCatalog.CatalogEntry workflow = catalog.require(request.workflowKey());
        Map<String, Object> variables = request.variables() == null ? Map.of() : request.variables();
        catalog.validateForm(catalog.startForm(workflow), variables);
        catalog.validateWorkflowData(workflow, variables);
        String businessKey = request.businessKey() == null || request.businessKey().isBlank()
                ? "W6W-" + UUID.randomUUID() : request.businessKey().trim();
        WorkflowInstance instance = engine.startInstance(workflow.definition().getMetadata().getKey(), businessKey,
                variables);
        audit.record(instance.getId(), instance.getWorkflowKey(), businessKey, null, "local-demo", "INSTANCE_STARTED",
                Map.of("workflowVersion", workflow.definition().getMetadata().getVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(instance);
    }

    @PostMapping("/events/{eventName}")
    public ResponseEntity<Map<String, Object>> signal(@PathVariable("eventName") String eventName,
                                                       @RequestBody SignalRequest request) {
        if (request.businessKey() == null || request.businessKey().isBlank())
            throw new IllegalArgumentException("businessKey is required for inbound signal correlation");
        List<String> instanceIds = engine.correlateSignal(eventName, request.businessKey(), request.workflowKey(), request.payload());
        if (instanceIds.isEmpty()) throw new NoSuchElementException("No workflow is waiting for event '" + eventName
                + "' with business key '" + request.businessKey() + "'");
        for (String instanceId : instanceIds) {
            WorkflowInstance instance = engine.getInstance(instanceId);
            audit.record(instanceId, instance.getWorkflowKey(), instance.getBusinessKey(), null, "inbound-event",
                    "INBOUND_SIGNAL_RECEIVED", Map.of("eventName", eventName));
        }
        return ResponseEntity.accepted().body(Map.of("eventName", eventName, "correlated", instanceIds.size(), "instanceIds", instanceIds));
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks(@RequestParam(name = "user", defaultValue = "demo.user") String user,
                                           @RequestParam(name = "roles", defaultValue = "") String roles,
                                           @RequestParam(name = "query", defaultValue = "") String query,
                                           @RequestParam(name = "status", defaultValue = "all") String status) {
        List<String> groups = Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return engine.getTasksForUser(user, groups).stream().filter(t -> matches(t, query, status))
                .map(this::taskSummary).toList();
    }

    @GetMapping("/tasks/admin")
    public List<Map<String, Object>> allTasks(@RequestParam(name = "query", defaultValue = "") String query) {
        return engine.getTasksForUser(null, List.of()).stream().filter(t -> matches(t, query, "all"))
                .map(this::taskSummary).toList();
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> task(@PathVariable("taskId") String taskId) {
        TaskInstance task = engine.getTask(taskId);
        if (task == null) throw new NoSuchElementException("Task not found: " + taskId);
        WorkflowInstance instance = engine.getInstance(task.getInstanceId());
        WorkflowCatalog.CatalogEntry workflow = catalog.require(instance.getWorkflowKey());
        JsonNode form = catalog.form(workflow, catalog.stepForm(instance.getWorkflowKey(), task.getStepId().replaceFirst("^step_", "").replace('_', '-')));
        Map<String, Object> result = taskSummary(task);
        result.put("workflowName", workflow.definition().getMetadata().getName());
        result.put("form", form);
        result.put("variables", instance.getVariables());
        result.put("outcomes", stepOutcomes(workflow, task.getStepId()));
        return result;
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<Void> claim(@PathVariable("taskId") String taskId, @RequestBody UserRequest request) {
        TaskInstance task = requireTask(taskId); WorkflowInstance instance = engine.getInstance(task.getInstanceId());
        engine.claimTask(taskId, user(request));
        audit.record(instance.getId(), instance.getWorkflowKey(), instance.getBusinessKey(), taskId, user(request), "TASK_CLAIMED", Map.of());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskId}/release")
    public ResponseEntity<Void> release(@PathVariable("taskId") String taskId) {
        TaskInstance task = requireTask(taskId); WorkflowInstance instance = engine.getInstance(task.getInstanceId());
        engine.releaseTask(taskId);
        audit.record(instance.getId(), instance.getWorkflowKey(), instance.getBusinessKey(), taskId,
                task.getAssignee() == null ? "local-demo" : task.getAssignee(), "TASK_RELEASED", Map.of());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> complete(@PathVariable("taskId") String taskId, @RequestBody CompleteTaskRequest request) {
        TaskInstance task = engine.getTask(taskId);
        if (task == null) throw new NoSuchElementException("Task not found: " + taskId);
        WorkflowInstance instance = engine.getInstance(task.getInstanceId());
        WorkflowCatalog.CatalogEntry workflow = catalog.require(instance.getWorkflowKey());
        String formPath = catalog.stepForm(instance.getWorkflowKey(), task.getStepId().replaceFirst("^step_", "").replace('_', '-'));
        if (formPath != null) catalog.validateForm(catalog.form(workflow, formPath), request.data());
        Map<String, Object> updatedData = new LinkedHashMap<>(instance.getVariables());
        if (request.data() != null) updatedData.putAll(request.data());
        var runningInstance = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult();
        if (runningInstance == null) throw new IllegalStateException("Workflow instance is no longer active: " + instance.getId());
        catalog.validateWorkflowData(runningInstance.getProcessDefinitionId(), updatedData);
        if (request.outcome() != null && !request.outcome().isBlank() && !stepOutcomes(workflow, task.getStepId()).containsKey(request.outcome()))
            throw new IllegalArgumentException("Outcome '" + request.outcome() + "' is not defined for this task");
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request.data() != null) variables.putAll(request.data());
        if (request.outcome() != null && !request.outcome().isBlank()) variables.put("outcome", request.outcome());
        engine.completeTask(taskId, user(request), variables);
        audit.record(instance.getId(), instance.getWorkflowKey(), instance.getBusinessKey(), taskId, user(request), "TASK_COMPLETED",
                Map.of("outcome", request.outcome() == null ? "" : request.outcome()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/bulk-claim")
    public Map<String, Object> bulkClaim(@RequestBody BulkClaimRequest request) {
        List<String> claimed = new ArrayList<>();
        for (String id : request.taskIds()) {
            TaskInstance task = requireTask(id); WorkflowInstance instance = engine.getInstance(task.getInstanceId());
            engine.claimTask(id, user(request)); claimed.add(id);
            audit.record(instance.getId(), instance.getWorkflowKey(), instance.getBusinessKey(), id, user(request), "TASK_CLAIMED", Map.of("bulk", true));
        }
        return Map.of("claimed", claimed.size(), "taskIds", claimed);
    }

    @GetMapping("/instances")
    public List<Map<String, Object>> instances(@RequestParam(name = "query", defaultValue = "") String query,
                                               @RequestParam(name = "status", defaultValue = "all") String status,
                                               @RequestParam(name = "limit", defaultValue = "200") int limit) {
        int pageSize = Math.max(1, Math.min(limit, 500));
        List<HistoricProcessInstance> all = processEngine.getHistoryService().createHistoricProcessInstanceQuery()
                .orderByProcessInstanceStartTime().desc().listPage(0, pageSize);
        return all.stream().map(this::instanceSummary).filter(i -> matchesInstance(i, query, status)).toList();
    }

    @GetMapping("/instances/{instanceId}")
    public Map<String, Object> instance(@PathVariable("instanceId") String instanceId) {
        WorkflowInstance instance = engine.getInstance(instanceId);
        if (instance == null) throw new NoSuchElementException("Instance not found: " + instanceId);
        List<Map<String, Object>> timeline = processEngine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId).orderByHistoricActivityInstanceStartTime().asc().list().stream()
                .map(this::activitySummary).toList();
        Map<String, Object> result = instanceSummary(instance);
        result.put("variables", instance.getVariables()); result.put("timeline", timeline);
        result.put("tasks", processEngine.getTaskService().createTaskQuery().processInstanceId(instanceId).list().stream()
                .map(t -> taskSummary(engine.getTask(t.getId()))).toList());
        return result;
    }

    @PostMapping("/instances/{instanceId}/admin")
    public ResponseEntity<Void> admin(@PathVariable("instanceId") String instanceId, @RequestBody AdminRequest request) {
        if (request.action() == null || request.action().isBlank()) throw new IllegalArgumentException("An admin action is required");
        WorkflowInstance instance = engine.getInstance(instanceId);
        if (instance == null) throw new NoSuchElementException("Instance not found: " + instanceId);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        if (request.reason() != null) auditDetails.put("reason", request.reason());
        if (request.taskId() != null) auditDetails.put("taskId", request.taskId());
        if (request.assignee() != null) auditDetails.put("assignee", request.assignee());
        switch (request.action().toLowerCase(Locale.ROOT)) {
            case "suspend" -> engine.suspendInstance(instanceId);
            case "resume" -> engine.resumeInstance(instanceId);
            case "cancel" -> engine.cancelInstance(instanceId, request.reason() == null ? "Cancelled by local operator" : request.reason());
            case "reassign" -> {
                if (request.taskId() == null || request.assignee() == null || request.assignee().isBlank())
                    throw new IllegalArgumentException("taskId and assignee are required for reassign");
                requireTask(request.taskId());
                processEngine.getTaskService().setAssignee(request.taskId(), request.assignee());
            }
            default -> throw new IllegalArgumentException("Unsupported admin action: " + request.action());
        }
        audit.record(instanceId, instance.getWorkflowKey(), instance.getBusinessKey(), request.taskId(), "local.operator",
                "INSTANCE_" + request.action().toUpperCase(Locale.ROOT), auditDetails);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dead-letter-jobs")
    public List<WorkflowFailure> deadLetterJobs() {
        return engine.getDeadLetterJobs();
    }

    @PostMapping("/dead-letter-jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLetterJob(@PathVariable("jobId") String jobId,
                                                    @RequestBody(required = false) DeadLetterRetryRequest request) {
        int retries = request == null || request.retries() == null ? 3 : request.retries();
        WorkflowFailure failedJob = engine.getDeadLetterJobs().stream().filter(job -> jobId.equals(job.id())).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Dead-letter job not found: " + jobId));
        engine.retryDeadLetterJob(jobId, retries);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("retries", retries);
        if (request != null && request.reason() != null && !request.reason().isBlank()) details.put("reason", request.reason());
        audit.record(failedJob.instanceId(), failedJob.workflowKey(), failedJob.businessKey(), jobId,
                "local-demo", "DEAD_LETTER_JOB_RETRIED", details);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit.csv")
    public ResponseEntity<String> auditCsv(@RequestParam(name = "limit", defaultValue = "200") int limit) {
        StringBuilder csv = new StringBuilder("id,instanceId,workflowKey,businessKey,taskId,actor,action,details,createdAt\n");
        audit.list(null, limit).forEach(row -> csv.append(csv(row.get("id"))).append(',')
                .append(csv(row.get("instanceId"))).append(',').append(csv(row.get("workflowKey"))).append(',')
                .append(csv(row.get("businessKey"))).append(',').append(csv(row.get("taskId"))).append(',')
                .append(csv(row.get("actor"))).append(',').append(csv(row.get("action"))).append(',')
                .append(csv(row.get("details"))).append(',').append(csv(row.get("createdAt"))).append('\n'));
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=w6w-audit.csv")
                .header("Content-Type", "text/csv; charset=utf-8").body(csv.toString());
    }

    @GetMapping("/audit/events")
    public List<Map<String, Object>> auditEvents(@RequestParam(name = "instanceId", required = false) String instanceId,
                                                  @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return audit.list(instanceId, limit);
    }

    @GetMapping("/instances/{instanceId}/audit")
    public List<Map<String, Object>> instanceAudit(@PathVariable("instanceId") String instanceId,
                                                    @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return audit.list(instanceId, limit);
    }

    private Map<String, Object> taskSummary(TaskInstance task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", task.getId()); item.put("instanceId", task.getInstanceId()); item.put("name", task.getName());
        item.put("stepId", task.getStepId()); item.put("assignee", task.getAssignee());
        item.put("priority", task.getPriority()); item.put("dueDate", task.getDueDate()); item.put("formKey", task.getFormKey());
        WorkflowInstance instance = engine.getInstance(task.getInstanceId());
        if (instance != null) {
            item.put("businessKey", instance.getBusinessKey()); item.put("workflowKey", instance.getWorkflowKey());
            item.put("workflowName", catalog.require(instance.getWorkflowKey()).definition().getMetadata().getName());
        }
        return item;
    }

    private TaskInstance requireTask(String taskId) {
        TaskInstance task = engine.getTask(taskId);
        if (task == null) throw new NoSuchElementException("Task not found: " + taskId);
        return task;
    }

    private Map<String, Object> instanceSummary(HistoricProcessInstance historical) {
        WorkflowInstance instance = engine.getInstance(historical.getId());
        return instanceSummary(instance);
    }

    private Map<String, Object> instanceSummary(WorkflowInstance instance) {
        Map<String, Object> item = new LinkedHashMap<>();
        String currentStep = instance.getCurrentStage();
        if ((currentStep == null || currentStep.isBlank()) && "RUNNING".equals(instance.getStatus())) {
            currentStep = processEngine.getTaskService().createTaskQuery()
                    .processInstanceId(instance.getId()).list().stream()
                    .map(Task::getTaskDefinitionKey).filter(Objects::nonNull).findFirst().orElse(currentStep);
        }
        item.put("id", instance.getId()); item.put("workflowKey", instance.getWorkflowKey()); item.put("businessKey", instance.getBusinessKey());
        item.put("status", instance.getStatus()); item.put("resolution", instance.getResolution());
        item.put("currentStep", currentStep); item.put("currentStage", stageName(instance.getWorkflowKey(), currentStep));
        item.put("workflowName", catalog.require(instance.getWorkflowKey()).definition().getMetadata().getName());
        return item;
    }

    private String stageName(String workflowKey, String activityId) {
        if (activityId == null) return "";
        var definition = catalog.require(workflowKey).definition();
        String normalized = activityId.startsWith("step_") ? activityId.substring(5).replace('_', '-') : activityId;
        for (var stage : definition.getStages()) {
            if (activityId.equals("end_" + stage.getId().replace('-', '_'))) return stage.getName() == null ? stage.getId() : stage.getName();
            for (var step : stage.getSteps()) if (step.getId().equals(normalized)) return stage.getName() == null ? stage.getId() : stage.getName();
        }
        return activityId;
    }

    private Map<String, Object> activitySummary(HistoricActivityInstance activity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("activityId", activity.getActivityId()); item.put("name", activity.getActivityName());
        item.put("type", activity.getActivityType()); item.put("assignee", activity.getAssignee());
        item.put("startedAt", activity.getStartTime() == null ? null : activity.getStartTime().toInstant().toString());
        item.put("endedAt", activity.getEndTime() == null ? null : activity.getEndTime().toInstant().toString());
        return item;
    }

    private Map<String, String> stepOutcomes(WorkflowCatalog.CatalogEntry workflow, String taskId) {
        for (var stage : workflow.definition().getStages()) for (var step : stage.getSteps())
            if (("step_" + step.getId().replace('-', '_')).equals(taskId)) return step.getOutcomes() == null ? Map.of() : step.getOutcomes();
        return Map.of();
    }

    private boolean matches(TaskInstance t, String query, String status) {
        if (!"all".equalsIgnoreCase(status)) return false;
        String q = query.toLowerCase(Locale.ROOT);
        return q.isBlank() || t.getName().toLowerCase(Locale.ROOT).contains(q) || t.getId().toLowerCase(Locale.ROOT).contains(q)
                || t.getInstanceId().toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean matchesInstance(Map<String, Object> i, String query, String status) {
        String q = query.toLowerCase(Locale.ROOT);
        boolean queryMatches = q.isBlank() || String.valueOf(i.get("businessKey")).toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(i.get("workflowName")).toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(i.get("id")).toLowerCase(Locale.ROOT).contains(q);
        return queryMatches && ("all".equalsIgnoreCase(status) || status.equalsIgnoreCase(String.valueOf(i.get("status"))));
    }

    private String user(UserRequest request) {
        if (request.user() == null || request.user().isBlank()) throw new IllegalArgumentException("A local demo user is required");
        return request.user().trim();
    }

    private String user(CompleteTaskRequest request) {
        if (request.user() == null || request.user().isBlank()) throw new IllegalArgumentException("A local demo user is required");
        return request.user().trim();
    }

    private String user(BulkClaimRequest request) {
        if (request.user() == null || request.user().isBlank()) throw new IllegalArgumentException("A local demo user is required");
        return request.user().trim();
    }

    private String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public record StartRequest(String workflowKey, String businessKey, Map<String, Object> variables) {}
    public record SignalRequest(String workflowKey, String businessKey, Map<String, Object> payload) {}
    public record UserRequest(String user) {}
    public record CompleteTaskRequest(String user, String outcome, Map<String, Object> data) {}
    public record BulkClaimRequest(String user, List<String> taskIds) {}
    public record AdminRequest(String action, String taskId, String assignee, String reason) {}
    public record DeadLetterRetryRequest(Integer retries, String reason) {}

    @RestControllerAdvice
    static class ApiErrors {
        @ExceptionHandler(NoSuchElementException.class)
        ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage())); }
        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
        ResponseEntity<Map<String, String>> badRequest(RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }
}
