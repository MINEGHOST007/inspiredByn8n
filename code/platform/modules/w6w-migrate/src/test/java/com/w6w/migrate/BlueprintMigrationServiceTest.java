package com.w6w.migrate;

import com.w6w.migrate.pdf.PdfBlueprintExtractor;
import com.w6w.migrate.mapper.BlueprintToCwmMapper;
import com.w6w.migrate.model.BlueprintIntermediateModel;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateCaseType;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStage;
import com.w6w.migrate.model.BlueprintIntermediateModel.IntermediateStep;
import com.w6w.governance.EligibilityChecker;
import com.w6w.test.ScenarioRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintMigrationServiceTest {
    @TempDir Path temp;

    @Test
    void unsupportedBlueprintCaseIsRejectedWithoutWritingPartialWorkflow() throws Exception {
        File pdf = new File("../../../../Pega Blueprint - Travel Management Customer Service.pdf");
        if (!pdf.exists()) pdf = new File("../../../Pega Blueprint - Travel Management Customer Service.pdf");
        assertThat(pdf).exists();

        var migrations = new BlueprintMigrationService(
                new PdfBlueprintExtractor(), new BlueprintToCwmMapper(), new EligibilityChecker()).migratePdf(pdf);
        assertThat(migrations).hasSize(3);
        var migration = migrations.get(0);
        assertThat(migration.generated()).isFalse();
        assertThat(migration.draft()).isNull();
        assertThat(migration.report().getUnsupportedCount()).isPositive();
        assertThat(migration.report().toMarkdown()).contains("eligibility:** REJECT", "REJECT-MG-01",
                "Confidence", "APPROXIMATED", "UNSUPPORTED");

        Path target = temp.resolve("migration-dry-run-must-not-exist");
        var planned = new MigrationFileWriter().write(migration, new PdfBlueprintExtractor().extract(pdf).getCaseTypes().get(0), target, true);
        assertThat(planned).containsOnlyKeys("migration-report.md");
        assertThat(target).doesNotExist();

        Path generated = temp.resolve("migration-rejected");
        new MigrationFileWriter().write(migration, new PdfBlueprintExtractor().extract(pdf).getCaseTypes().get(0), generated, false);
        assertThat(generated.resolve("workflow.cwm.yaml")).doesNotExist();
        assertThat(generated.resolve("schemas/data.schema.json")).doesNotExist();
        assertThat(generated.resolve("migration-report.md")).content()
                .contains("REJECTED", "REJECT-MG-01", "UNSUPPORTED");

        File binary = new File("../../../../Travel_Management_Customer_Service.blueprint");
        if (!binary.exists()) binary = new File("../../../Travel_Management_Customer_Service.blueprint");
        assertThat(binary).exists();
        File unsupportedBinary = binary;
        assertThatThrownBy(() -> new BlueprintMigrationService().migratePdf(unsupportedBinary))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Proprietary .blueprint binaries are not decoded");
    }

    @Test
    void supportedBlueprintSkeletonGeneratesCompilablePackageAndCompletesSmokeScenario() throws Exception {
        BlueprintIntermediateModel model = new BlueprintIntermediateModel();
        model.setBlueprintId("BP-SIMPLE-001");
        model.getPersonas().add("Service Agent");
        model.getPersonas().add("Property Manager");
        IntermediateCaseType caseType = new IntermediateCaseType();
        caseType.setName("Simple Purchase Request");
        caseType.setDescription("A low-complexity request workflow.");
        IntermediateStage stage = new IntermediateStage();
        stage.setName("Request");
        stage.getSteps().add(new IntermediateStep("Submit Request", "userTask"));
        stage.getSteps().add(new IntermediateStep("Validate Request", "serviceTask"));
        stage.getSteps().add(new IntermediateStep("Complete Request", "end"));
        caseType.getStages().add(stage);
        model.getCaseTypes().add(caseType);

        var migration = new BlueprintMigrationService(null, new BlueprintToCwmMapper(), new EligibilityChecker())
                .migrateModel(model).get(0);
        assertThat(migration.generated()).isTrue();
        assertThat(migration.draft().getRoles()).contains("service-agent", "property-manager", "migration_reviewer");
        assertThat(migration.draft().getMetadata().getDataClassification()).isEqualTo("restricted");
        assertThat(migration.report().toMarkdown()).contains("provisional restricted", "migration_reviewer", "no-op stub");

        Path target = temp.resolve("simple-purchase-request");
        var packageFiles = new MigrationFileWriter().write(migration, caseType, target, false);
        Path workflowFile = target.resolve("workflow.cwm.yaml");
        Path scenarioFile = target.resolve("tests/scenarios/migration-smoke.yaml");
        assertThat(packageFiles).containsKeys("workflow.cwm.yaml", "schemas/data.schema.json",
                "forms/submit-request.json", "tests/scenarios/migration-smoke.yaml", "migration-report.md");
        assertThat(new com.w6w.compiler.CwmLinter().lint(com.w6w.model.CwmParser.parse(workflowFile.toFile()), workflowFile).hasErrors()).isFalse();
        assertThat(new com.w6w.compiler.CwmToBpmnCompiler().compile(com.w6w.model.CwmParser.parse(workflowFile.toFile())))
                .contains("migration.stub.simple-purchase-request.validate-request");

        var smoke = new ScenarioRunner().runScenario(scenarioFile.toFile(), workflowFile.toFile());
        assertThat(smoke.passed).withFailMessage("Smoke scenario failed: %s", smoke.errorMessage).isTrue();
    }
}
