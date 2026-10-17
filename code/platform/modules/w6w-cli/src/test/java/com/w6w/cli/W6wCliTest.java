package com.w6w.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class W6wCliTest {
    @TempDir Path temp;

    @Test
    void rendersPlainLanguageSummaryAndMermaidDiagram() throws Exception {
        Path packageDir = new WorkflowScaffolder().create(temp, "render-example", "Render Example", "ops-team", "demo", "request-with-sla");
        Path output = temp.resolve("workflow-summary.md");
        new W6wCli(temp).render(packageDir.resolve("workflow.cwm.yaml"), output);
        assertThat(output).content().contains("# Render Example", "## Stages and steps", "Review request", "PT24H", "```mermaid", "flowchart TD");
    }

    @Test
    void migratesBlueprintPdfToReviewablePackagesAndDryRunDoesNotWrite() throws Exception {
        Path repository = Path.of("../../../../").toAbsolutePath().normalize();
        Path pdf = repository.resolve("Pega Blueprint - Travel Management Customer Service.pdf");
        assertThat(pdf).exists();
        Path dryOutput = temp.resolve("dry-output");
        new W6wCli(repository).migratePdf(new String[]{pdf.toString(), "--out", dryOutput.toString(), "--dry-run"});
        assertThat(dryOutput).doesNotExist();

        Path output = temp.resolve("generated");
        new W6wCli(repository).migratePdf(new String[]{pdf.toString(), "--out", output.toString()});
        try (var packages = Files.list(output)) {
            var packageFolders = packages.filter(Files::isDirectory).toList();
            assertThat(packageFolders).hasSize(3);
            assertThat(packageFolders).allSatisfy(folder -> assertThat(folder.resolve("workflow.cwm.yaml")).exists());
            assertThat(packageFolders).allSatisfy(folder -> assertThat(folder.resolve("migration-report.md")).content().contains("Manual actions", "UNSUPPORTED"));
        }
    }
}
