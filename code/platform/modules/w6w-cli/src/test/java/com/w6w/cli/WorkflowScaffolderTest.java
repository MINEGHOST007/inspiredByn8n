package com.w6w.cli;

import com.w6w.compiler.CwmLinter;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.model.CwmParser;
import com.w6w.test.ScenarioRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowScaffolderTest {
    @TempDir Path temp;

    @Test
    void allSrsTemplatesCreateValidRunnableCwmPackages() throws Exception {
        WorkflowScaffolder scaffolder = new WorkflowScaffolder();
        List<String> templates = List.of("approval", "intake-and-fulfil", "multi-level-approval", "request-with-sla");
        int index = 0;
        for (String template : templates) {
            String key = "test-workflow-" + (++index);
            Path directory = scaffolder.create(temp, key, "Test Workflow " + index, "test-team", "test", template);
            Path cwm = directory.resolve("workflow.cwm.yaml");
            var workflow = CwmParser.parse(cwm.toFile());
            assertThat(new CwmLinter().lint(workflow).hasErrors()).isFalse();
            assertThat(new CwmToBpmnCompiler().compile(workflow)).contains("process id=\"" + key + "\"");
            assertThat(directory.resolve("forms/request.json")).exists();
            assertThat(directory.resolve("schemas/data.schema.json")).exists();
            Path scenario = directory.resolve("tests/scenarios/happy-path.yaml");
            assertThat(scenario).exists();
            assertThat(directory.resolve("README.md")).exists();
            ScenarioRunner.TestExecutionResult execution = new ScenarioRunner().runScenario(scenario.toFile(), cwm.toFile());
            assertThat(execution.passed).as(template + " happy path: " + execution.errorMessage).isTrue();
        }
    }

    @Test
    void refusesUnsafeOrExistingScaffoldTargets() throws Exception {
        WorkflowScaffolder scaffolder = new WorkflowScaffolder();
        assertThatThrownBy(() -> scaffolder.create(temp, "../outside", "Unsafe", "owner", "team", "approval"))
                .isInstanceOf(IllegalArgumentException.class);
        Path created = scaffolder.create(temp, "duplicate", "Duplicate", "owner", "team", "approval");
        assertThatThrownBy(() -> scaffolder.create(temp, "duplicate", "Duplicate", "owner", "team", "approval"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThat(created).exists();
    }
}
