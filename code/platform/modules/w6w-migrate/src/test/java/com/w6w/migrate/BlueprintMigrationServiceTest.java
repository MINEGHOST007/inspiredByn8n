package com.w6w.migrate;

import com.w6w.migrate.pdf.PdfBlueprintExtractor;
import com.w6w.migrate.mapper.BlueprintToCwmMapper;
import com.w6w.governance.EligibilityChecker;
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
}
