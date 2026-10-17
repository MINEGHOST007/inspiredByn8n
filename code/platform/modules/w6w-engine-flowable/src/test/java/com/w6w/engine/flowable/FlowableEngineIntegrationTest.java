package com.w6w.engine.flowable;

import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.model.CwmParser;
import com.w6w.model.WorkflowDefinition;
import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowInstance;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.ActionRegistry;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowableEngineIntegrationTest {

    private ProcessEngine processEngine;
    private FlowableWorkflowEngineAdapter adapter;
    private ActionRegistry actionRegistry;

    @BeforeEach
    void setUp() {
        actionRegistry = new ActionRegistry();

        // Register dummy action handlers for automated steps
        actionRegistry.register("refund.validateRequest", ctx -> {
            assertThat(ctx.getIdempotencyKey()).isNotEmpty();
            return ActionResult.success();
        });
        actionRegistry.register("refund.generateId", ctx -> ActionResult.success(Collections.singletonMap("caseId", "REF-1001")));
        actionRegistry.register("notify.email", ctx -> ActionResult.success());
        actionRegistry.register("refund.retrieveRecords", ctx -> ActionResult.success());
        actionRegistry.register("refund.checkPolicyAdherence", ctx -> ActionResult.success(Collections.singletonMap("policyAdherencePassed", true)));
        actionRegistry.register("refund.initiateTransaction", ctx -> {
            assertThat(ctx.getIdempotencyKey()).contains("initiate-refund-transaction");
            return ActionResult.success(Collections.singletonMap("disbursementTransactionId", "TXN-99881"));
        });
        actionRegistry.register("refund.updateLedger", ctx -> ActionResult.success());
        actionRegistry.register("refund.generateReceipt", ctx -> ActionResult.success());

        FlowableActionExecutor actionExecutor = new FlowableActionExecutor(actionRegistry);

        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:flowable_int_test;DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate(StandaloneInMemProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        
        Map<Object, Object> beans = new HashMap<>();
        beans.put("w6wActionExecutor", actionExecutor);
        beans.put("w6wDirectoryService", new com.w6w.runtime.DirectoryLookupService());
        beans.put("w6wSlaService", new com.w6w.runtime.SlaDeadlineService());
        config.setBeans(beans);

        processEngine = config.buildProcessEngine();

        adapter = new FlowableWorkflowEngineAdapter(
            processEngine.getRepositoryService(),
            processEngine.getRuntimeService(),
            processEngine.getTaskService(),
            processEngine.getHistoryService()
        );
    }

    @AfterEach
    void tearDown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void testRefundProcessingEndToEnd() throws Exception {
        // 1. Compile CWM to BPMN
        File cwmFile = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        if (!cwmFile.exists()) {
            cwmFile = new File("../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        }
        assertThat(cwmFile).exists();
        WorkflowDefinition workflow = CwmParser.parse(cwmFile);

        CwmToBpmnCompiler compiler = new CwmToBpmnCompiler();
        String bpmnXml = compiler.compile(workflow);
        assertThat(bpmnXml).isNotEmpty();

        // 2. Deploy to embedded Flowable engine
        String deploymentId = adapter.deploy("refund-processing", "refund-processing", bpmnXml);
        assertThat(deploymentId).isNotEmpty();

        // 3. Start instance idempotently with business key
        Map<String, Object> initialVars = new HashMap<>();
        initialVars.put("guestName", "Alice Walker");
        initialVars.put("refundAmount", 250.0);
        initialVars.put("refundReason", "Room AC issue");
        initialVars.put("refundType", "OriginalPaymentMethod");

        WorkflowInstance instance = adapter.startInstance("refund-processing", "CASE-BK-001", initialVars);
        assertThat(instance).isNotNull();
        assertThat(instance.getId()).isNotEmpty();
        assertThat(instance.getStatus()).isEqualTo("RUNNING");
        WorkflowInstance duplicateStart = adapter.startInstance("refund-processing", "CASE-BK-001", Collections.singletonMap("ignored", true));
        assertThat(duplicateStart.getId()).isEqualTo(instance.getId());
        adapter.suspendInstance(instance.getId());
        assertThat(adapter.getInstance(instance.getId()).getStatus()).isEqualTo("SUSPENDED");
        adapter.resumeInstance(instance.getId());
        assertThat(adapter.getInstance(instance.getId()).getStatus()).isEqualTo("RUNNING");

        // 4. Verify first human task: 'Receive Request'
        List<TaskInstance> tasks = adapter.getTasksForUser("agent.smith", Collections.singletonList("service_agent"));
        assertThat(tasks).isNotEmpty();
        TaskInstance receiveTask = tasks.get(0);
        assertThat(receiveTask.getName()).isEqualTo("Receive Request");

        // Claim and complete receive task
        adapter.claimTask(receiveTask.getId(), "agent.smith");
        adapter.completeTask(receiveTask.getId(), "agent.smith", Collections.singletonMap("intakeComplete", true));

        // Automated steps execute: validateRequest -> generateId -> sendConfirmation -> retrieveRecords -> checkPolicyAdherence
        // Next human task: 'Review Eligibility Details'
        tasks = adapter.getTasksForUser("agent.smith", Collections.singletonList("service_agent"));
        assertThat(tasks).isNotEmpty();
        TaskInstance reviewTask = tasks.get(0);
        assertThat(reviewTask.getName()).isEqualTo("Review Eligibility Details");

        // Complete review task
        Map<String, Object> reviewVars = new HashMap<>();
        reviewVars.put("policyAdherencePassed", true);
        reviewVars.put("outcome", "eligible");
        adapter.completeTask(reviewTask.getId(), "agent.smith", reviewVars);

        // Next approval task: 'Request Refund Approval'
        tasks = adapter.getTasksForUser("manager.jones", Collections.emptyList());
        if (tasks.isEmpty()) {
            // Task assigned via candidate lookup
            tasks = processEngine.getTaskService().createTaskQuery().list().stream()
                .map(t -> adapter.getTask(t.getId())).toList();
        }
        assertThat(tasks).isNotEmpty();
        TaskInstance approvalTask = tasks.stream()
            .filter(t -> t.getName().equals("Request Refund Approval"))
            .findFirst()
            .orElseThrow();

        // Complete approval task with 'approve' outcome
        adapter.completeTask(approvalTask.getId(), "manager.jones", Collections.singletonMap("outcome", "approve"));

        // Automated steps execute: sendApprovalNotification -> initiateTransaction -> updateLedger -> generateReceipt -> closeCase
        // 5. Assert instance concluded successfully with resolution 'Approved'
        WorkflowInstance completed = adapter.getInstance(instance.getId());
        assertThat(completed).isNotNull();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getVariables().get("disbursementTransactionId")).isEqualTo("TXN-99881");
        assertThat(completed.getVariables().get("caseId")).isEqualTo("REF-1001");
        WorkflowInstance duplicateCompletedStart = adapter.startInstance("refund-processing", "CASE-BK-001", Collections.emptyMap());
        assertThat(duplicateCompletedStart.getId()).isEqualTo(instance.getId());
        assertThat(duplicateCompletedStart.getStatus()).isEqualTo("COMPLETED");
    }
}
