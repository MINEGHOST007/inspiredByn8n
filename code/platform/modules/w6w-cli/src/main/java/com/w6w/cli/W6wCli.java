package com.w6w.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w6w.compiler.CwmLinter;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.compiler.DecisionTableCompiler;
import com.w6w.model.CwmParser;
import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;
import com.w6w.test.ScenarioRunner;
import com.w6w.migrate.BlueprintMigrationService;
import com.w6w.migrate.MigrationFileWriter;
import com.w6w.migrate.pdf.PdfBlueprintExtractor;
import com.w6w.migrate.mapper.BlueprintMappingRules;
import com.w6w.migrate.mapper.BlueprintToCwmMapper;
import com.w6w.governance.EligibilityChecker;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

public final class W6wCli {
    private final Path root;

    W6wCli(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            String encodedArgs = System.getenv("W6W_CLI_ARGS");
            if (encodedArgs != null && !encodedArgs.isBlank()) args = new ObjectMapper().readValue(encodedArgs, String[].class);
        }
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) { help(); return; }
        Path root = Path.of(System.getenv().getOrDefault("W6W_ROOT", System.getProperty("w6w.root", ".")));
        new W6wCli(root).run(args);
    }

    private void run(String[] args) throws Exception {
        switch (args[0]) {
            case "new" -> scaffold(Arrays.copyOfRange(args, 1, args.length));
            case "validate" -> validate(resolve(arg(args, 1, "workflow file")));
            case "compile" -> compile(resolve(arg(args, 1, "workflow file")), args.length > 2 ? resolve(args[2]) : null);
            case "compile-decision" -> compileDecision(resolve(arg(args, 1, "decision table YAML")), args.length > 2 ? resolve(args[2]) : null);
            case "render" -> render(resolve(arg(args, 1, "workflow file")), args.length > 2 ? resolve(args[2]) : null);
            case "test" -> test(resolve(arg(args, 1, "workflow file")), args.length > 2 ? resolve(args[2]) : null);
            case "deploy-local" -> deployLocal(arg(args, 1, "workflow key"));
            case "diff" -> diff(resolve(arg(args, 1, "workflow file")));
            case "migrate-pdf" -> migratePdf(Arrays.copyOfRange(args, 1, args.length));
            default -> throw new IllegalArgumentException("Unknown command '" + args[0] + "'. Run w6w help.");
        }
    }

    private void scaffold(String[] args) throws Exception {
        Map<String, String> options = options(args);
        String key = required(options, "key"), template = required(options, "template");
        String name = options.getOrDefault("name", title(key));
        String owner = options.getOrDefault("owner", "workflow-owner");
        String domain = options.getOrDefault("domain", "general");
        Path created = new WorkflowScaffolder().create(root, key, name, owner, domain, template);
        System.out.println("Created " + root.relativize(created));
        System.out.println("Next: w6w validate " + root.relativize(created.resolve("workflow.cwm.yaml")));
        System.out.println("Review the generated roles, data classification, and form fields before use.");
    }

    private void validate(Path file) throws Exception {
        WorkflowDefinition workflow = CwmParser.parse(file.toFile());
        CwmLinter.LintResult result = new CwmLinter().lint(workflow);
        result.getIssues().forEach(System.out::println);
        if (result.hasErrors()) throw new IllegalArgumentException("Validation failed for " + file);
        new CwmToBpmnCompiler().compile(workflow);
        System.out.println("Valid: " + workflow.getMetadata().getKey() + " " + workflow.getMetadata().getVersion());
    }

    private void compile(Path file, Path output) throws Exception {
        WorkflowDefinition workflow = checked(file);
        if (output == null) output = root.resolve("code/workflows/build/" + workflow.getMetadata().getKey() + ".bpmn20.xml");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new CwmToBpmnCompiler().compile(workflow));
        System.out.println("Compiled " + workflow.getMetadata().getKey() + " to " + output);
    }

    void compileDecision(Path file, Path output) throws Exception {
        DecisionTableCompiler compiler = new DecisionTableCompiler();
        var table = compiler.parse(file);
        if (output == null) output = root.resolve("code/workflows/build/decisions/" + table.getKey() + ".dmn");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, compiler.compile(table));
        System.out.println("Compiled decision table " + table.getKey() + " to " + output);
    }

    void render(Path file, Path output) throws Exception {
        WorkflowDefinition workflow = checked(file);
        if (output == null) output = file.resolveSibling("workflow-summary.md");
        StringBuilder doc = new StringBuilder("# ").append(workflow.getMetadata().getName()).append("\n\n")
                .append(workflow.getMetadata().getDescription()).append("\n\n")
                .append("- Key: `").append(workflow.getMetadata().getKey()).append("`\n- Version: `")
                .append(workflow.getMetadata().getVersion()).append("`\n- Owner: `")
                .append(workflow.getMetadata().getOwner()).append("`\n- Roles: ")
                .append(String.join(", ", workflow.getRoles())).append("\n\n## Stages and steps\n\n");
        for (StageDefinition stage : workflow.getStages()) {
            doc.append("### ").append(stage.getName()).append(stage.isAlternate() ? " (alternate)" : "").append("\n\n");
            for (StepDefinition step : stage.getSteps()) {
                doc.append("- **").append(step.getName()).append("** (`").append(step.getType()).append("`)");
                if (step.getAssignee() != null && step.getAssignee().getRole() != null) doc.append(" — role: `").append(step.getAssignee().getRole()).append("`");
                if (step.getSla() != null && step.getSla().getDeadline() != null) doc.append(" — deadline: `").append(step.getSla().getDeadline()).append("`");
                doc.append('\n');
                if (step.getOutcomes() != null) step.getOutcomes().forEach((label, target) -> doc.append("  - ").append(label).append(" → ").append(target).append('\n'));
            }
            if (stage.getResolution() != null) doc.append("\nResolution: **").append(stage.getResolution()).append("**\n");
            doc.append('\n');
        }
        doc.append("## Diagram\n\n```mermaid\nflowchart TD\n");
        for (StageDefinition stage : workflow.getStages()) {
            String stageNode = node(stage.getId());
            doc.append("  subgraph ").append(stageNode).append("[\"").append(mermaid(stage.getName())).append("\"]\n");
            String previous = null;
            for (StepDefinition step : stage.getSteps()) {
                String stepNode = node(step.getId());
                doc.append("    ").append(stepNode).append("[\"").append(mermaid(step.getName())).append("\"]\n");
                if (previous != null) doc.append("    ").append(previous).append(" --> ").append(stepNode).append('\n');
                previous = stepNode;
            }
            doc.append("  end\n");
        }
        doc.append("```\n");
        Files.writeString(output, doc.toString());
        System.out.println("Rendered workflow summary and diagram to " + output);
    }

    private void test(Path file, Path scenarios) throws Exception {
        WorkflowDefinition workflow = checked(file);
        if (scenarios == null) scenarios = file.getParent().resolve("tests/scenarios");
        if (!Files.isDirectory(scenarios)) throw new IllegalArgumentException("Scenario folder not found: " + scenarios);
        List<Path> files;
        try (Stream<Path> paths = Files.list(scenarios)) {
            files = paths.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml")).sorted().toList();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("No YAML scenarios found in " + scenarios);
        ScenarioRunner runner = new ScenarioRunner();
        List<ScenarioRunner.TestExecutionResult> results = new ArrayList<>();
        for (Path scenario : files) {
            ScenarioRunner.TestExecutionResult result = runner.runScenario(scenario.toFile(), file.toFile());
            results.add(result);
            System.out.println(result.scenarioName + ": " + (result.passed ? "PASS" : "FAIL - " + result.errorMessage));
        }
        Path report = root.resolve("artifacts/test_results/" + workflow.getMetadata().getKey() + "-cli-results.xml");
        ScenarioRunner.writeJunitXml(results, report.toFile());
        if (results.stream().anyMatch(result -> !result.passed)) throw new IllegalStateException("Scenario failures; see " + report);
        System.out.println("All " + results.size() + " scenario(s) passed. Report: " + report);
    }

    private void deployLocal(String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/api/v1/admin/workflows/reload?key=" +
                        java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Local deployment failed (HTTP " + response.statusCode() + "): " + response.body());
        System.out.println("Local workflow catalog reloaded: " + response.body());
    }

    private void diff(Path file) throws Exception {
        String relative = root.relativize(file.toAbsolutePath().normalize()).toString();
        Process process = new ProcessBuilder("git", "diff", "HEAD", "--", relative).directory(root.toFile()).inheritIO().start();
        int code = process.waitFor();
        if (code != 0) throw new IllegalStateException("git diff exited with " + code);
    }

    void migratePdf(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Missing Blueprint PDF path");
        Path source = resolve(args[0]);
        Path output = root.resolve("code/workflows/domains/migrated").resolve(slug(source.getFileName().toString().replaceFirst("(?i)\\.pdf$", "")));
        Path mappingRulesFile = null;
        boolean dryRun = false;
        for (int i = 1; i < args.length; i++) {
            if ("--dry-run".equals(args[i])) dryRun = true;
            else if ("--out".equals(args[i]) && i + 1 < args.length) output = resolve(args[++i]);
            else if ("--rules".equals(args[i]) && i + 1 < args.length) mappingRulesFile = resolve(args[++i]);
            else throw new IllegalArgumentException("Unexpected migrate-pdf option: " + args[i]);
        }
        BlueprintMappingRules rules = mappingRulesFile == null ? BlueprintMappingRules.defaults() : BlueprintMappingRules.fromFile(mappingRulesFile);
        var cases = new PdfBlueprintExtractor().extract(source.toFile()).getCaseTypes();
        var migrations = new BlueprintMigrationService(new PdfBlueprintExtractor(), new BlueprintToCwmMapper(rules), new EligibilityChecker()).migratePdf(source.toFile());
        if (cases.size() != migrations.size()) throw new IllegalStateException("Migration adapter returned inconsistent case results");
        MigrationFileWriter writer = new MigrationFileWriter();
        int generatedDrafts = 0;
        int rejectedCases = 0;
        for (int i = 0; i < migrations.size(); i++) {
            var migration = migrations.get(i);
            String caseSlug = slug(cases.get(i).getName());
            Path caseOutput = output.resolve(caseSlug);
            Map<String, String> planned = writer.write(migration, cases.get(i), caseOutput, dryRun);
            System.out.println(migration.report().getEligibilityDecision() + " " + cases.get(i).getName()
                    + (dryRun ? " (dry run)" : "") + ": " + planned.keySet());
            if (migration.generated()) generatedDrafts++;
            else rejectedCases++;
        }
        System.out.println("Migration complete: " + generatedDrafts + " reviewable draft(s), "
                + rejectedCases + " rejected case(s) out of " + migrations.size()
                + "; " + (dryRun ? "no files written" : "reports at " + output));
        if (generatedDrafts > 0) System.out.println("Generated drafts require human review and validation before local deployment.");
    }

    private WorkflowDefinition checked(Path file) throws Exception {
        WorkflowDefinition workflow = CwmParser.parse(file.toFile());
        CwmLinter.LintResult lint = new CwmLinter().lint(workflow);
        lint.getIssues().forEach(System.out::println);
        if (lint.hasErrors()) throw new IllegalArgumentException("Workflow validation failed");
        new CwmToBpmnCompiler().compile(workflow);
        return workflow;
    }

    private Path resolve(String path) { Path value = Path.of(path); return (value.isAbsolute() ? value : root.resolve(value)).normalize(); }
    private static String arg(String[] args, int index, String name) { if (args.length <= index) throw new IllegalArgumentException("Missing " + name); return args[index]; }
    private static String required(Map<String, String> values, String key) { String value = values.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing --" + key); return value; }
    private static String title(String key) { return Arrays.stream(key.split("-")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).reduce((a,b)->a+" "+b).orElse(key); }
    private static String node(String id) { return id.replaceAll("[^A-Za-z0-9_]", "_"); }
    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "migrated-workflow" : slug;
    }
    private static String mermaid(String value) { return value == null ? "" : value.replace("\"", "'").replace("[", "(").replace("]", ")"); }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 == args.length) throw new IllegalArgumentException("Expected --option value, got " + args[i]);
            options.put(args[i].substring(2), args[++i]);
        }
        return options;
    }

    private static void help() {
        System.out.println("W6W workflow CLI\n"
                + "  new --key KEY --template approval|intake-and-fulfil|multi-level-approval|request-with-sla [--name NAME --owner OWNER --domain DOMAIN]\n"
                + "  validate WORKFLOW.yaml | compile WORKFLOW.yaml [OUTPUT.bpmn] | compile-decision TABLE.yaml [OUTPUT.dmn]\n"
                + "  render WORKFLOW.yaml [OUTPUT.md]\n"
                + "  test WORKFLOW.yaml [SCENARIOS_DIR] | deploy-local WORKFLOW_KEY | diff WORKFLOW.yaml\n"
                + "  migrate-pdf BLUEPRINT.pdf [--out DIRECTORY] [--dry-run] [--rules RULES.yaml]");
    }
}
