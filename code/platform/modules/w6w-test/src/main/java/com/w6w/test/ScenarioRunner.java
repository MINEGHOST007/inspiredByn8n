package com.w6w.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.engine.flowable.FlowableActionExecutor;
import com.w6w.engine.flowable.FlowableWorkflowEngineAdapter;
import com.w6w.model.CwmParser;
import com.w6w.model.WorkflowDefinition;
import com.w6w.port.TaskInstance;
import com.w6w.port.WorkflowInstance;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.ActionRegistry;
import com.w6w.runtime.DirectoryLookupService;
import com.w6w.runtime.SlaDeadlineService;
import com.w6w.runtime.StartupActionValidator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ScenarioRunner {
    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static class TestExecutionResult {
        public String scenarioName;
        public boolean passed;
        public long durationMs;
        public String errorMessage;
    }

    public TestExecutionResult runScenario(File scenarioFile, File cwmFile) throws Exception {
        return runScenario(scenarioFile, cwmFile,
                "jdbc:h2:mem:scenario_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    public TestExecutionResult runScenario(File scenarioFile, File cwmFile, String jdbcUrl) throws Exception {
        long start = System.currentTimeMillis();
        TestExecutionResult result = new TestExecutionResult();

        ScenarioDefinition scenario = YAML_MAPPER.readValue(scenarioFile, ScenarioDefinition.class);
        result.scenarioName = scenario.getName();

        WorkflowDefinition workflow = CwmParser.parse(cwmFile);

        ActionRegistry actionRegistry = new ActionRegistry();
        registerMockHandlers(actionRegistry);
        new StartupActionValidator(actionRegistry).validateWorkflowActions(workflow);

        FlowableActionExecutor actionExecutor = new FlowableActionExecutor(actionRegistry);

        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl(jdbcUrl);
        config.setDatabaseSchemaUpdate(StandaloneInMemProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(true);

        Map<Object, Object> beans = new HashMap<>();
        beans.put("w6wActionExecutor", actionExecutor);
        beans.put("w6wDirectoryService", new DirectoryLookupService());
        beans.put("w6wSlaService", new SlaDeadlineService());
        config.setBeans(beans);

        ProcessEngine engine = config.buildProcessEngine();
        FlowableWorkflowEngineAdapter adapter = new FlowableWorkflowEngineAdapter(
            engine.getRepositoryService(), engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService(),
            engine.getManagementService()
        );

        try {
            CwmToBpmnCompiler compiler = new CwmToBpmnCompiler();
            String bpmnXml = compiler.compile(workflow);
            adapter.deploy(workflow.getMetadata().getKey(), workflow.getMetadata().getKey(), bpmnXml);

            WorkflowInstance instance = null;

            for (ScenarioDefinition.ScenarioStep step : scenario.getSteps()) {
                if ("startWorkflow".equals(step.getAction())) {
                    String bk = "SCENARIO-" + System.currentTimeMillis();
                    instance = adapter.startInstance(scenario.getWorkflowKey(), bk, scenario.getInitialData());
                } else if ("completeTask".equals(step.getAction()) || "completeApproval".equals(step.getAction())) {
                    if (instance == null) throw new IllegalStateException("Scenario must start a workflow before completing a task");
                    String taskDefinitionKey = "step_" + step.getStepId().replace('-', '_');
                    org.flowable.task.api.Task target = awaitTask(engine, instance.getId(), taskDefinitionKey);
                    if (target == null) {
                        throw new IllegalStateException("No active task '" + step.getStepId() + "' found for scenario instance " + instance.getId());
                    }
                    Map<String, Object> vars = new HashMap<>(step.getFormData());
                    if (step.getOutcome() != null) {
                        vars.put("outcome", step.getOutcome());
                    }
                    adapter.completeTask(target.getId(), step.getUser(), vars);
                }
            }

            if (instance != null) {
                WorkflowInstance finished = awaitInstance(adapter, instance.getId(),
                        scenario.getAssertions().getFinalResolution() == null ? null : "COMPLETED");
                if (scenario.getAssertions().getFinalResolution() != null) {
                    if (!"COMPLETED".equals(finished.getStatus())) {
                        throw new AssertionError("Workflow did not complete, status was: " + finished.getStatus());
                    }
                    if (!scenario.getAssertions().getFinalResolution().equals(finished.getResolution())) {
                        throw new AssertionError("Expected resolution '" + scenario.getAssertions().getFinalResolution()
                                + "' but got '" + finished.getResolution() + "'");
                    }
                }
            }

            result.passed = true;
        } catch (Throwable t) {
            log.error("Scenario '{}' failed: {}", scenario.getName(), t.getMessage(), t);
            result.passed = false;
            result.errorMessage = t.getMessage();
        } finally {
            engine.close();
            result.durationMs = System.currentTimeMillis() - start;
        }

        return result;
    }

    private org.flowable.task.api.Task awaitTask(ProcessEngine engine, String instanceId, String definitionKey) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        org.flowable.task.api.Task task;
        do {
            task = engine.getTaskService().createTaskQuery().processInstanceId(instanceId)
                    .taskDefinitionKey(definitionKey).singleResult();
            if (task != null) return task;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return null;
    }

    private WorkflowInstance awaitInstance(FlowableWorkflowEngineAdapter adapter, String instanceId, String status) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        WorkflowInstance instance;
        do {
            instance = adapter.getInstance(instanceId);
            if (status == null || status.equals(instance.getStatus())) return instance;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return instance;
    }

    /** Runs the sample scenarios against a persistent local H2 file database.
     * Arguments: CWM path, scenario directory, result XML path, database file path.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected: <workflow.cwm.yaml> <scenarios-dir> <results.xml> <database-file>");
        }

        File cwmFile = Path.of(args[0]).toFile();
        File scenariosDir = Path.of(args[1]).toFile();
        File resultsFile = Path.of(args[2]).toFile();
        Path databaseFile = Path.of(args[3]).toAbsolutePath();
        Files.createDirectories(databaseFile.getParent());

        File[] scenarios = scenariosDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (scenarios == null || scenarios.length == 0) {
            throw new IllegalArgumentException("No YAML scenarios found in " + scenariosDir.getAbsolutePath());
        }
        Arrays.sort(scenarios, Comparator.comparing(File::getName));

        String dbUrl = "jdbc:h2:file:" + databaseFile.toString().replace('\\', '/') + ";AUTO_SERVER=TRUE";
        ScenarioRunner runner = new ScenarioRunner();
        List<TestExecutionResult> results = new ArrayList<>();
        for (File scenario : scenarios) {
            TestExecutionResult result = runner.runScenario(scenario, cwmFile, dbUrl);
            results.add(result);
            System.out.printf("%s: %s%s%n", result.scenarioName,
                    result.passed ? "PASS" : "FAIL",
                    result.errorMessage == null ? "" : " - " + result.errorMessage);
        }
        writeJunitXml(results, resultsFile);
        long failures = results.stream().filter(result -> !result.passed).count();
        System.out.printf("Results: %s; local H2 database: %s%n",
                resultsFile.getAbsolutePath(), databaseFile);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private void registerMockHandlers(ActionRegistry actionRegistry) {
        actionRegistry.register("refund.validateRequest", ctx -> ActionResult.success());
        actionRegistry.register("refund.generateId", ctx -> ActionResult.success(Collections.singletonMap("caseId", "REF-9021")));
        actionRegistry.register("notify.email", ctx -> ActionResult.success());
        actionRegistry.register("refund.retrieveRecords", ctx -> ActionResult.success());
        actionRegistry.register("refund.checkPolicyAdherence", ctx -> ActionResult.success(Collections.singletonMap("policyAdherencePassed", true)));
        actionRegistry.register("refund.initiateTransaction", ctx -> ActionResult.success(Collections.singletonMap("disbursementTransactionId", "TXN-SCENARIO")));
        actionRegistry.register("refund.updateLedger", ctx -> ActionResult.success());
        actionRegistry.register("refund.generateReceipt", ctx -> ActionResult.success());
        actionRegistry.register("refund.recordDenial", ctx -> ActionResult.success());
        actionRegistry.register("refund.generateDenialLetter", ctx -> ActionResult.success());
    }

    public static void writeJunitXml(List<TestExecutionResult> results, File outputFile) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        int failures = (int) results.stream().filter(r -> !r.passed).count();
        long totalTime = results.stream().mapToLong(r -> r.durationMs).sum();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append(String.format("<testsuite name=\"W6WScenarioTests\" tests=\"%d\" failures=\"%d\" time=\"%.3f\">\n",
            results.size(), failures, totalTime / 1000.0));

        for (TestExecutionResult r : results) {
            xml.append(String.format("  <testcase name=\"%s\" time=\"%.3f\">\n", escape(r.scenarioName), r.durationMs / 1000.0));
            if (!r.passed) {
                xml.append(String.format("    <failure message=\"%s\">%s</failure>\n", escape(r.errorMessage), escape(r.errorMessage)));
            }
            xml.append("  </testcase>\n");
        }

        xml.append("</testsuite>\n");

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(xml.toString());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
