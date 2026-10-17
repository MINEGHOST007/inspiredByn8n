package com.w6w.migrate;

import com.w6w.migrate.mapper.BlueprintMappingRules;
import com.w6w.migrate.mapper.BlueprintToCwmMapper;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateCaseType;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStage;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStep;
import com.w6w.migrate.report.ConversionReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintMappingRulesTest {
    @TempDir Path temp;

    @Test
    void packagedRulesAreVersionedAndUnknownSourceTypesFailClosed() {
        BlueprintMappingRules rules = BlueprintMappingRules.defaults();
        assertThat(rules.version()).isEqualTo(1);
        assertThat(rules.forSourceType("approval").targetType()).isEqualTo("approval");
        assertThat(rules.forSourceType("futureConstruct").strategy()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void overrideRuleChangesTheGeneratedStepMapping() throws Exception {
        Path override = temp.resolve("override.yaml");
        Files.writeString(override, "version: 1\nstepMappings:\n  decision:\n    strategy: USER_TASK\n    targetType: userTask\n    confidence: high\n    reason: Approved local mapping.\n");
        BlueprintMappingRules rules = BlueprintMappingRules.fromFile(override);
        IntermediateCaseType caseType = new IntermediateCaseType();
        caseType.setName("Decision case");
        IntermediateStage stage = new IntermediateStage();
        stage.setName("Review");
        stage.getSteps().add(new IntermediateStep("Review decision", "decision"));
        caseType.getStages().add(stage);

        var report = new ConversionReport("BP-OVERRIDE", "Decision case");
        var workflow = new BlueprintToCwmMapper(rules).mapToWorkflow(caseType, "BP-OVERRIDE", report);
        assertThat(workflow.getStages().get(0).getSteps().get(0).getType()).isEqualTo("userTask");
        assertThat(workflow.getStages().get(0).getSteps().get(0).getForm()).isEqualTo("forms/review-decision.json");
        assertThat(report.toMarkdown()).contains("Approved local mapping.");
    }

    @Test
    void unsupportedMappingVersionsAreRejected() throws Exception {
        Path override = temp.resolve("future-version.yaml");
        Files.writeString(override, "version: 2\nstepMappings:\n  task:\n    strategy: USER_TASK\n    targetType: userTask\n");
        assertThatThrownBy(() -> BlueprintMappingRules.fromFile(override))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported Blueprint mapping rules version");
    }
}
