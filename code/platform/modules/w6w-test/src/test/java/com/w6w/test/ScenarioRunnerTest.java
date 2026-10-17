package com.w6w.test;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioRunnerTest {

    @Test
    void testExecuteAllScenariosAndEmitResultsXml() throws Exception {
        File cwmFile = new File("../../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        if (!cwmFile.exists()) {
            cwmFile = new File("../../workflows/domains/customer-service/refund-processing/workflow.cwm.yaml");
        }
        assertThat(cwmFile).exists();

        File scenariosDir = new File(cwmFile.getParentFile(), "tests/scenarios");
        assertThat(scenariosDir).exists();

        File[] scenarioFiles = scenariosDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        assertThat(scenarioFiles).isNotNull().isNotEmpty();

        ScenarioRunner runner = new ScenarioRunner();
        List<ScenarioRunner.TestExecutionResult> results = new ArrayList<>();

        for (File scenarioFile : scenarioFiles) {
            ScenarioRunner.TestExecutionResult res = runner.runScenario(scenarioFile, cwmFile);
            results.add(res);
            assertThat(res.passed)
                .withFailMessage("Scenario '%s' failed: %s", res.scenarioName, res.errorMessage)
                .isTrue();
        }

        // Write JUnit results.xml to root artifacts/test_results/results.xml
        File rootArtifacts = new File("../../../../artifacts/test_results");
        if (!rootArtifacts.exists()) {
            rootArtifacts.mkdirs();
        }
        File resultsFile = new File(rootArtifacts, "results.xml");
        ScenarioRunner.writeJunitXml(results, resultsFile);

        assertThat(resultsFile).exists();
        assertThat(resultsFile.length()).isGreaterThan(0);
    }
}
