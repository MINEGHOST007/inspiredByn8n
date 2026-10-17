package com.w6w.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:w6w-app-test;DB_CLOSE_DELAY=-1",
        "w6w.workflows-dir=${user.dir}/../../../../code/workflows",
        "w6w.runtime-mode=local-demo",
        "flowable.database-schema-update=true"
})
@AutoConfigureMockMvc
class WorkflowApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void servesWorkspaceAndRunsApprovalWorkflowThroughTheApi() throws Exception {
        assertThat(getClass().getResource("/static/index.html")).isNotNull();
        JsonNode apiDoc = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(apiDoc.path("info").path("version").asText()).isEqualTo("1.0.0");
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());

        JsonNode workflows = json.readTree(mvc.perform(get("/api/workflows"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workflows.get(0).path("key").asText()).isEqualTo("refund-processing");

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
        complete(review.path("id").asText(), "agent.smith", "eligible", Map.of(
                "policyAdherencePassed", true, "calculatedRefundAmount", 125.0,
                "compensationType", "BonusPoints", "reviewerComments", "Reviewed"));

        JsonNode approval = findTask("manager.jones", "property_manager", businessKey, "step_request_refund_approval");
        assertThat(approval.path("dueDate").isMissingNode()).isFalse();
        complete(approval.path("id").asText(), "manager.jones", "approve", Map.of());

        JsonNode finished = json.readTree(mvc.perform(get("/api/instances/" + instanceId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
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

    private JsonNode findTask(String user, String role, String businessKey, String step) throws Exception {
        JsonNode tasks = json.readTree(mvc.perform(get("/api/tasks").param("user", user).param("roles", role))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode task : tasks) if (businessKey.equals(task.path("businessKey").asText())
                && step.equals(task.path("stepId").asText())) return task;
        throw new AssertionError("No task " + step + " found for " + businessKey + " in " + tasks);
    }

    private void complete(String taskId, String user, String outcome, Map<String, Object> data) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("user", user); request.put("outcome", outcome); request.put("data", data);
        mvc.perform(post("/api/tasks/" + taskId + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request))).andExpect(status().isNoContent());
    }
}
