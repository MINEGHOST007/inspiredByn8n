package com.w6w.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.engine.flowable.FlowableWorkflowEngineAdapter;
import com.w6w.model.CwmParser;
import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowInstance;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.ActionRegistry;
import com.w6w.runtime.WorkflowDataSchemaRegistry;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:w6w-app-test;DB_CLOSE_DELAY=-1",
        "w6w.workflows-dir=${user.dir}/../../../../code/workflows",
        "w6w.runtime-mode=local-demo",
        "flowable.database-schema-update=true",
        "flowable.async-executor-activate=true"
})
@AutoConfigureMockMvc
class WorkflowApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private FlowableWorkflowEngineAdapter workflowEngine;
    @Autowired private ActionRegistry actionRegistry;
    @Autowired private WorkflowDataSchemaRegistry dataSchemas;
    @Autowired private ProcessEngine processEngine;

    @Test
    void servesWorkspaceRunsApprovalWorkflowAndExposesDeadLetterQueueApi() throws Exception {
        assertThat(getClass().getResource("/static/index.html")).isNotNull();
        JsonNode apiDoc = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(apiDoc.path("info").path("version").asText()).isEqualTo("1.0.0");
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        JsonNode deadLetters = json.readTree(mvc.perform(get("/api/dead-letter-jobs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(deadLetters.isArray()).isTrue();
        assertThat(deadLetters).isEmpty();
        mvc.perform(post("/api/dead-letter-jobs/missing-job/retry")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"retries\":3}"))
                .andExpect(status().isNotFound());
        JsonNode reloaded = json.readTree(mvc.perform(post("/api/v1/admin/workflows/reload").param("key", "refund-processing"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reloaded.path("status").asText()).isEqualTo("reloaded");

        JsonNode workflows = json.readTree(mvc.perform(get("/api/workflows"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(java.util.stream.StreamSupport.stream(workflows.spliterator(), false)
                .anyMatch(workflow -> "refund-processing".equals(workflow.path("key").asText()))).isTrue();

        Map<String, Object> missing = Map.of("workflowKey", "refund-processing", "businessKey", "UI-INVALID", "variables", Map.of());
        mvc.perform(post("/api/instances").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(missing)))
                .andExpect(status().isBadRequest());

        String businessKey = "UI-IT-" + System.nanoTime();
        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("associatedComplaint", "CMP-TEST"); startData.put("guestName", "Synthetic user");
        startData.put("guestEmail", "sample@example.test"); startData.put("guestLoyaltyTier", "Gold");
        startData.put("refundAmount", 125.0); startData.put("refundReason", "Automated integration test");
        startData.put("refundType", "OriginalPaymentMethod");
        JsonNode started = json.readTree(mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("workflowKey", "refund-processing", "businessKey", businessKey, "variables", startData))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String instanceId = started.path("id").asText();

        JsonNode intake = findTask("agent.smith", "service_agent", businessKey, "step_receive_request");
        JsonNode intakeDetails = json.readTree(mvc.perform(get("/api/tasks/" + intake.path("id").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(intakeDetails.path("form").path("properties").has("guestName")).isTrue();
        complete(intake.path("id").asText(), "agent.smith", null, startData);

        JsonNode review = findTask("agent.smith", "service_agent", businessKey, "step_review_eligibility_details");
        String invalidDataResponse = mvc.perform(post("/api/tasks/" + review.path("id").asText() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("user", "agent.smith", "outcome", "eligible", "data", Map.of(
                                "policyAdherencePassed", true, "refundType", "Cryptocurrency")))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(invalidDataResponse).contains("refundType has an unsupported value");
        complete(review.path("id").asText(), "agent.smith", "eligible", Map.of(
                "policyAdherencePassed", true, "calculatedRefundAmount", 125.0,
                "compensationType", "BonusPoints", "reviewerComments", "Reviewed"));

        JsonNode approval = findTask("manager.jones", "property_manager", businessKey, "step_request_refund_approval");
        assertThat(approval.path("dueDate").isMissingNode()).isFalse();
        complete(approval.path("id").asText(), "manager.jones", "approve", Map.of());

        JsonNode finished = awaitCompleted(instanceId);
        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("resolution").asText()).isEqualTo("Approved");
        assertThat(finished.path("currentStage").asText()).isEqualTo("Closure");
        assertThat(finished.path("timeline").size()).isGreaterThan(5);
        JsonNode events = json.readTree(mvc.perform(get("/api/instances/" + instanceId + "/audit"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(events.toString()).contains("INSTANCE_STARTED", "TASK_COMPLETED", "manager.jones");
        String auditCsv = mvc.perform(get("/api/audit.csv"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(auditCsv).contains("actor,action,details", "INSTANCE_STARTED", businessKey);
    }

    @Test
    void deadLetterQueueApiReplaysFailedServiceTaskAndAuditsTheOperatorAction() throws Exception {
        String action = "test.apiDeadLetter";
        AtomicBoolean actionSucceeds = new AtomicBoolean(false);
        actionRegistry.register(action, context -> {
            if (!actionSucceeds.get()) throw new IllegalStateException("synthetic API retry failure");
            return ActionResult.success();
        });
        var workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: api-dead-letter-test
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: submit
                        type: userTask
                        assignee:
                          role: operations
                      - id: flaky-action
                        name: Flaky action
                        type: serviceTask
                        action: test.apiDeadLetter
                        onError:
                          retries: 2
                          backoff: PT1S
                          onFailure: deadLetter
                """);
        workflowEngine.deploy("api-dead-letter-test", "API dead-letter test", new CwmToBpmnCompiler().compile(workflow));
        WorkflowInstance instance = workflowEngine.startInstance("api-dead-letter-test", "API-DLQ-" + System.nanoTime(), Map.of());
        TaskInstance submit = workflowEngine.getTasksForUser(null, List.of()).stream()
                .filter(task -> instance.getId().equals(task.getInstanceId())).findFirst().orElseThrow();
        workflowEngine.completeTask(submit.getId(), "operator", Map.of());

        JsonNode queue = awaitDeadLetter(instance.getId());
        JsonNode failure = java.util.stream.StreamSupport.stream(queue.spliterator(), false)
                .filter(job -> instance.getId().equals(job.path("instanceId").asText())).findFirst().orElseThrow();
        assertThat(failure.path("stepName").asText()).isEqualTo("Flaky action");
        actionSucceeds.set(true);
        mvc.perform(post("/api/dead-letter-jobs/" + failure.path("id").asText() + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("retries", 2, "reason", "Downstream recovered"))))
                .andExpect(status().isNoContent());
        awaitDeadLetterRemoval(failure.path("id").asText());
        awaitWorkflowCompleted(instance.getId());

        JsonNode events = json.readTree(mvc.perform(get("/api/instances/" + instance.getId() + "/audit"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(events.toString()).contains("DEAD_LETTER_JOB_RETRIED", "Downstream recovered");
    }

    @Test
    void rejectsServiceActionOutputThatBreaksTheWorkflowDataSchema() throws Exception {
        String action = "test.invalidDataOutput";
        actionRegistry.register(action, context -> ActionResult.success(Map.of("count", 0)));
        var workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: invalid-data-output-test
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: submit
                        type: userTask
                        assignee:
                          role: operations
                      - id: invalid-output
                        type: serviceTask
                        action: test.invalidDataOutput
                        onError:
                          retries: 1
                          backoff: PT1S
                          onFailure: deadLetter
                """);
        workflowEngine.deploy("invalid-data-output-test", "Invalid data output test", new CwmToBpmnCompiler().compile(workflow));
        String processDefinitionId = processEngine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("invalid-data-output-test").latestVersion().singleResult().getId();
        dataSchemas.register(processDefinitionId, json.readTree("""
                {"type":"object","required":["count"],"properties":{"count":{"type":"integer","minimum":1}}}
                """));
        WorkflowInstance instance = workflowEngine.startInstance("invalid-data-output-test", "INVALID-DATA-" + System.nanoTime(), Map.of("count", 1));
        TaskInstance submit = workflowEngine.getTasksForUser(null, List.of()).stream()
                .filter(task -> instance.getId().equals(task.getInstanceId())).findFirst().orElseThrow();
        workflowEngine.completeTask(submit.getId(), "operator", Map.of());

        JsonNode queue = awaitDeadLetter(instance.getId());
        JsonNode failure = java.util.stream.StreamSupport.stream(queue.spliterator(), false)
                .filter(job -> instance.getId().equals(job.path("instanceId").asText())).findFirst().orElseThrow();
        assertThat(failure.path("errorMessage").asText()).contains("count is below its minimum");
    }

    @Test
    void deadlineBreachMovesTaskToEscalationRoleAndAuditsTheChange() throws Exception {
        var workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: sla-escalation-test
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                roles: [service_agent, operations]
                stages:
                  - id: process
                    resolution: Done
                    steps:
                      - id: review
                        name: Review request
                        type: userTask
                        assignee:
                          user: agent.smith
                        sla:
                          deadline: PT2S
                          onDeadline: escalate:operations
                """);
        workflowEngine.deploy("sla-escalation-test", "SLA escalation test", new CwmToBpmnCompiler().compile(workflow));
        WorkflowInstance instance = workflowEngine.startInstance("sla-escalation-test", "SLA-" + System.nanoTime(), Map.of());
        TaskInstance original = waitForTask(instance.getId(), "agent.smith", List.of());
        assertThat(original.getAssignee()).isEqualTo("agent.smith");

        TaskInstance escalated = waitForTask(instance.getId(), "operations.user", List.of("operations"));
        assertThat(escalated.getAssignee()).isNull();
        assertThat(escalated.getCandidateGroups()).contains("operations");
        assertThat(workflowEngine.getTasksForUser("agent.smith", List.of("service_agent")))
                .noneMatch(task -> instance.getId().equals(task.getInstanceId()));
        JsonNode auditEvents = json.readTree(mvc.perform(get("/api/instances/" + instance.getId() + "/audit"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(auditEvents.toString()).contains("SLA_DEADLINE_ESCALATED", "operations");
    }

    private TaskInstance waitForTask(String instanceId, String user, List<String> roles) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        do {
            TaskInstance task = workflowEngine.getTasksForUser(user, roles).stream()
                    .filter(candidate -> instanceId.equals(candidate.getInstanceId())).findFirst().orElse(null);
            if (task != null) return task;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No active task found for workflow instance " + instanceId + " as " + user + " / " + roles);
    }

    private JsonNode findTask(String user, String role, String businessKey, String step) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        JsonNode tasks = json.createArrayNode();
        do {
            tasks = json.readTree(mvc.perform(get("/api/tasks").param("user", user).param("roles", role))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (JsonNode task : tasks) if (businessKey.equals(task.path("businessKey").asText())
                    && step.equals(task.path("stepId").asText())) return task;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No task " + step + " found for " + businessKey + " in " + tasks);
    }

    private JsonNode awaitCompleted(String instanceId) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        JsonNode instance;
        do {
            instance = json.readTree(mvc.perform(get("/api/instances/" + instanceId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if ("COMPLETED".equals(instance.path("status").asText())) return instance;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Workflow " + instanceId + " did not complete: " + instance);
    }

    private JsonNode awaitDeadLetter(String instanceId) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        JsonNode queue;
        do {
            queue = json.readTree(mvc.perform(get("/api/dead-letter-jobs"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if (java.util.stream.StreamSupport.stream(queue.spliterator(), false)
                    .anyMatch(job -> instanceId.equals(job.path("instanceId").asText()))) return queue;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Service task did not enter the dead-letter queue for " + instanceId + ": " + queue);
    }

    private void awaitDeadLetterRemoval(String jobId) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        JsonNode queue;
        do {
            queue = json.readTree(mvc.perform(get("/api/dead-letter-jobs"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            boolean stillQueued = java.util.stream.StreamSupport.stream(queue.spliterator(), false)
                    .anyMatch(job -> jobId.equals(job.path("id").asText()));
            if (!stillQueued) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Dead-letter job was not removed after replay: " + jobId);
    }

    private void awaitWorkflowCompleted(String instanceId) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        WorkflowInstance instance;
        do {
            instance = workflowEngine.getInstance(instanceId);
            if ("COMPLETED".equals(instance.getStatus())) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Workflow was not completed after service task replay: " + instance);
    }

    private void complete(String taskId, String user, String outcome, Map<String, Object> data) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("user", user); request.put("outcome", outcome); request.put("data", data);
        mvc.perform(post("/api/tasks/" + taskId + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request))).andExpect(status().isNoContent());
    }
}
