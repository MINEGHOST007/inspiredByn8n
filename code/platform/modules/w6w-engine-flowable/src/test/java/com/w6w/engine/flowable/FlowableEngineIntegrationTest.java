package com.w6w.engine.flowable;

import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.compiler.DecisionTableCompiler;
import com.w6w.model.CwmParser;
import com.w6w.model.DecisionTableDefinition;
import com.w6w.model.WorkflowDefinition;
import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowInstance;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.ActionRegistry;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowableEngineIntegrationTest {

    private ProcessEngine processEngine;
    private DmnEngine dmnEngine;
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
        DmnEngineConfiguration dmnConfiguration = DmnEngineConfiguration.createStandaloneDmnEngineConfiguration();
        dmnConfiguration.setJdbcUrl("jdbc:h2:mem:flowable_int_test;DB_CLOSE_DELAY=-1");
        dmnConfiguration.setDatabaseSchemaUpdate(DmnEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        W6wDmnEngineConfigurator dmnConfigurator = (W6wDmnEngineConfigurator) new W6wDmnEngineConfigurator()
                .setDmnEngineConfiguration(dmnConfiguration);
        config.addConfigurator(dmnConfigurator);

        processEngine = config.buildProcessEngine();
        dmnEngine = dmnConfigurator.getConfiguredDmnEngine();
        beans.put("w6wDecisionExecutor", new FlowableDecisionExecutor(dmnEngine));

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
        if (dmnEngine != null) dmnEngine.close();
    }

    @Test
    void decisionStepExecutesVersionPinnedDmnAndRoutesByOutcome() throws Exception {
        Path packageRoot = Files.createTempDirectory("w6w-decision-flow-");
        Path decisions = Files.createDirectories(packageRoot.resolve("decisions"));
        Path tableFile = decisions.resolve("route.yaml");
        Files.writeString(tableFile, """
                apiVersion: w6w/decision/v1
                key: route
                name: Route request
                hitPolicy: FIRST
                inputs:
                  - id: amount-input
                    typeRef: number
                    expression: amount
                outputs:
                  - id: outcome-output
                    name: outcome
                    typeRef: string
                rules:
                  - id: approve-small
                    inputEntries: [\"<= 500\"]
                    outputEntries: ['\"approve\"']
                  - id: reject-large
                    inputEntries: [\"> 500\"]
                    outputEntries: ['\"reject\"']
                """);
        DecisionTableCompiler tableCompiler = new DecisionTableCompiler();
        var table = tableCompiler.parse(tableFile);
        String versionedKey = CwmToBpmnCompiler.versionedDecisionKey("decisions/route.yaml", "1.0.0");
        dmnEngine.getDmnRepositoryService().createDeployment().name(versionedKey)
                .addString(versionedKey + ".dmn", tableCompiler.compile(table, versionedKey)).deploy();

        Path workflowFile = packageRoot.resolve("workflow.cwm.yaml");
        Files.writeString(workflowFile, """
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: decision-routing-test
                  name: Decision routing test
                  version: 1.0.0
                  tier: 1
                  owner: test-team
                  dataClassification: internal
                roles: []
                stages:
                  - id: route
                    steps:
                      - id: evaluate-request
                        name: Evaluate request
                        type: decision
                        decision: decisions/route.yaml
                        outcomes:
                          approve: stage:approved
                          reject: stage:rejected
                  - id: approved
                    resolution: Approved
                    steps:
                      - id: finish-approved
                        type: end
                  - id: rejected
                    alternate: true
                    resolution: Rejected
                    steps:
                      - id: finish-rejected
                        type: end
                """);
        WorkflowDefinition workflow = CwmParser.parse(workflowFile.toFile());
        String bpmn = new CwmToBpmnCompiler().compile(workflow);
        assertThat(bpmn).contains("w6wDecisionExecutor", "route_v1_0_0", "allowedOutcomes");
        adapter.deploy("decision-routing-test", "Decision routing test", bpmn);

        WorkflowInstance approved = adapter.startInstance("decision-routing-test", "DECISION-APPROVE", Map.of("amount", 250));
        WorkflowInstance rejected = adapter.startInstance("decision-routing-test", "DECISION-REJECT", Map.of("amount", 900));
        assertThat(adapter.getInstance(approved.getId()).getStatus()).isEqualTo("COMPLETED");
        assertThat(adapter.getInstance(approved.getId()).getVariables()).containsEntry("outcome", "approve");
        assertThat(adapter.getInstance(approved.getId()).getResolution()).isEqualTo("Approved");
        assertThat(adapter.getInstance(rejected.getId()).getStatus()).isEqualTo("COMPLETED");
        assertThat(adapter.getInstance(rejected.getId()).getVariables()).containsEntry("outcome", "reject");
        assertThat(adapter.getInstance(rejected.getId()).getResolution()).isEqualTo("Rejected");
    }

    @Test
    void conditionGatewayRunsOrSkipsTheStepFromWorkflowData() throws Exception {
        WorkflowDefinition workflow = CwmParser.parse("""
                apiVersion: w6w/v1
                kind: Workflow
                metadata:
                  key: conditional-test
                  version: 1.0.0
                  owner: operations
                  dataClassification: internal
                stages:
                  - id: review
                    resolution: Done
                    steps:
                      - id: optional-review
                        name: Optional review
                        type: userTask
                        assignee:
                          role: operations
                        condition:
                          field: data.needsReview
                          operator: eq
                          value: true
                      - id: finish-review
                        name: Finish review
                        type: userTask
                        assignee:
                          role: operations
                """);
        adapter.deploy("conditional-test", "Conditional test", new CwmToBpmnCompiler().compile(workflow));

        WorkflowInstance skipped = adapter.startInstance("conditional-test", "CASE-SKIP", Map.of("needsReview", false));
        WorkflowInstance selected = adapter.startInstance("conditional-test", "CASE-RUN", Map.of("needsReview", true));

        List<TaskInstance> tasks = adapter.getTasksForUser(null, List.of());
        assertThat(tasks).filteredOn(task -> task.getInstanceId().equals(skipped.getId()))
                .extracting(TaskInstance::getName).containsExactly("Finish review");
        assertThat(tasks).filteredOn(task -> task.getInstanceId().equals(selected.getId()))
                .extracting(TaskInstance::getName).containsExactly("Optional review");
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
