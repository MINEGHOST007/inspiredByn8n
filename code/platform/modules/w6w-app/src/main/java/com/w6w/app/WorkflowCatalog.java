package com.w6w.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w6w.compiler.CwmLinter;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.compiler.DecisionTableCompiler;
import com.w6w.model.DecisionTableDefinition;
import com.w6w.model.CwmParser;
import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;
import com.w6w.port.WorkflowEnginePort;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.ActionRegistry;
import com.w6w.runtime.StartupActionValidator;
import com.w6w.runtime.WorkflowDataSchemaRegistry;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.engine.DmnEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class WorkflowCatalog implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCatalog.class);
    private final Path workflowsRoot;
    private final String runtimeMode;
    private final WorkflowEnginePort engine;
    private final RepositoryService repository;
    private final ActionRegistry actions;
    private final ObjectMapper json;
    private final DmnEngine dmnEngine;
    private final WorkflowDataSchemaRegistry dataSchemas;
    private final Map<String, CatalogEntry> entries = new ConcurrentHashMap<>();

    public WorkflowCatalog(@Value("${w6w.workflows-dir}") String workflowsDir,
                           @Value("${w6w.runtime-mode:local-demo}") String runtimeMode,
                           WorkflowEnginePort engine, RepositoryService repository,
                           ActionRegistry actions, ObjectMapper json, DmnEngine dmnEngine,
                           WorkflowDataSchemaRegistry dataSchemas) {
        this.workflowsRoot = Path.of(workflowsDir).toAbsolutePath().normalize();
        this.runtimeMode = runtimeMode;
        this.engine = engine;
        this.repository = repository;
        this.actions = actions;
        this.json = json;
        this.dmnEngine = dmnEngine;
        this.dataSchemas = dataSchemas;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!"local-demo".equalsIgnoreCase(runtimeMode)) {
            throw new IllegalStateException("This bundled app only supports local-demo mode. Configure host identity, actions, database, and security before production use.");
        }
        registerLocalDemoActions();
        if (!Files.isDirectory(workflowsRoot)) {
            throw new IllegalStateException("Workflow folder does not exist: " + workflowsRoot);
        }
        try (Stream<Path> files = Files.walk(workflowsRoot)) {
            for (Path file : files.filter(p -> p.getFileName().toString().equals("workflow.cwm.yaml")).sorted().toList()) {
                register(file);
            }
        }
        if (entries.isEmpty()) throw new IllegalStateException("No workflow.cwm.yaml definitions found under " + workflowsRoot);
        log.info("Loaded {} local workflow definition(s) from {}", entries.size(), workflowsRoot);
    }

    public synchronized CatalogEntry reload(String workflowKey) throws Exception {
        if (!"local-demo".equalsIgnoreCase(runtimeMode))
            throw new IllegalStateException("Workflow reload is only enabled in local-demo mode");
        if (!Files.isDirectory(workflowsRoot)) throw new IllegalStateException("Workflow folder does not exist: " + workflowsRoot);
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> files = Files.walk(workflowsRoot)) {
            for (Path file : files.filter(p -> p.getFileName().toString().equals("workflow.cwm.yaml")).toList()) {
                if (workflowKey.equals(CwmParser.parse(file.toFile()).getMetadata().getKey())) matches.add(file);
            }
        }
        if (matches.isEmpty()) throw new NoSuchElementException("Workflow definition file not found for key: " + workflowKey);
        if (matches.size() > 1) throw new IllegalArgumentException("Multiple workflow files use key: " + workflowKey);
        register(matches.get(0), true);
        return require(workflowKey);
    }

    private void registerLocalDemoActions() {
        Set<String> demoActions = Set.of("refund.validateRequest", "refund.generateId", "notify.email",
                "refund.retrieveRecords", "refund.checkPolicyAdherence", "refund.initiateTransaction",
                "refund.updateLedger", "refund.generateReceipt", "refund.recordDenial",
                "refund.generateDenialLetter");
        for (String name : demoActions) actions.register(name, context -> {
            if ("refund.generateId".equals(name)) return ActionResult.success(Map.of("caseId", "REF-" + context.getInstanceId().substring(0, 8)));
            if ("refund.checkPolicyAdherence".equals(name)) return ActionResult.success(Map.of("policyAdherencePassed", true));
            return ActionResult.success();
        });
    }

    private void register(Path file) throws Exception {
        register(file, false);
    }

    private void register(Path file, boolean replaceExisting) throws Exception {
        WorkflowDefinition definition = CwmParser.parse(file.toFile());
        JsonNode dataSchema = readDataSchema(file, definition);
        CwmLinter.LintResult lint = new CwmLinter().lint(definition, file);
        if (lint.hasErrors()) throw new IllegalArgumentException("Workflow " + definition.getMetadata().getKey() + " has lint errors: " + lint.getIssues());
        new StartupActionValidator(actions).validateWorkflowActions(definition);
        String bpm = new CwmToBpmnCompiler().compile(definition);
        if (dataSchema != null) bpm = includeDataSchemaFingerprint(bpm, dataSchema);
        deployDecisionTables(file, definition);
        List<ProcessDefinition> existing = repository.createProcessDefinitionQuery()
                .processDefinitionKey(definition.getMetadata().getKey()).orderByProcessDefinitionVersion().desc().list();
        if (existing.isEmpty()) {
            engine.deploy(definition.getMetadata().getKey(), definition.getMetadata().getName(), bpm);
        } else if (!deploymentMatches(existing.get(0), bpm)) {
            String deploymentId = engine.deploy(definition.getMetadata().getKey(), definition.getMetadata().getName(), bpm);
            log.info("Deployed changed workflow '{}' as version {} (deployment {})", definition.getMetadata().getKey(), existing.get(0).getVersion() + 1, deploymentId);
        } else {
            log.info("Workflow '{}' already deployed as version {}; keeping existing deployment", definition.getMetadata().getKey(), existing.get(0).getVersion());
        }
        ProcessDefinition deployed = repository.createProcessDefinitionQuery()
                .processDefinitionKey(definition.getMetadata().getKey()).orderByProcessDefinitionVersion().desc().listPage(0, 1)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Workflow was not deployed: " + definition.getMetadata().getKey()));
        if (dataSchema != null) dataSchemas.register(deployed.getId(), dataSchema);
        CatalogEntry entry = new CatalogEntry(definition, file.toAbsolutePath().normalize(), deployed.getId());
        if (replaceExisting) entries.put(definition.getMetadata().getKey(), entry);
        else if (entries.putIfAbsent(definition.getMetadata().getKey(), entry) != null)
            throw new IllegalArgumentException("Duplicate workflow key: " + definition.getMetadata().getKey());
    }

    private JsonNode readDataSchema(Path workflowFile, WorkflowDefinition definition) throws IOException {
        Object reference = definition.getData() == null ? null : definition.getData().get("schema");
        if (reference == null || reference.toString().isBlank()) return null;
        Path packageRoot = workflowFile.getParent().toAbsolutePath().normalize();
        Path schemaPath = packageRoot.resolve(reference.toString()).normalize();
        if (!schemaPath.startsWith(packageRoot) || !Files.isRegularFile(schemaPath))
            throw new IllegalArgumentException("Workflow data schema is missing or escapes workflow package: " + reference);
        JsonNode schema = json.readTree(schemaPath.toFile());
        if (schema == null || !schema.isObject())
            throw new IllegalArgumentException("Workflow data schema must contain a JSON object: " + reference);
        return schema;
    }

    private String includeDataSchemaFingerprint(String bpmn, JsonNode schema) throws IOException {
        byte[] canonicalSchema = json.writeValueAsBytes(schema);
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalSchema));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        int processStart = bpmn.indexOf(">", bpmn.indexOf("<process "));
        if (processStart < 0) throw new IllegalArgumentException("Compiler output has no BPMN process element");
        return bpmn.substring(0, processStart + 1) + "\n      <documentation>w6w-data-schema-sha256:" + fingerprint
                + "</documentation>" + bpmn.substring(processStart + 1);
    }

    private void deployDecisionTables(Path workflowFile, WorkflowDefinition definition) throws Exception {
        DecisionTableCompiler compiler = new DecisionTableCompiler();
        Path packageRoot = workflowFile.getParent().toAbsolutePath().normalize();
        Map<String, StepDefinition> references = new LinkedHashMap<>();
        for (StageDefinition stage : definition.getStages()) for (StepDefinition step : stage.getSteps()) {
            if ("decision".equals(step.getType())) references.putIfAbsent(step.getDecision(), step);
        }
        for (var reference : references.entrySet()) {
            Path source = packageRoot.resolve(reference.getKey()).normalize();
            if (!source.startsWith(packageRoot) || !Files.isRegularFile(source))
                throw new IllegalArgumentException("Decision table file is missing or escapes workflow package: " + reference.getKey());
            DecisionTableDefinition table = compiler.parse(source);
            String baseKey = source.getFileName().toString().replaceFirst("(?i)\\.(yaml|yml)$", "");
            if (!baseKey.equals(table.getKey())) throw new IllegalArgumentException("Decision file name must match decision table key '" + table.getKey() + "'");
            int outcomeColumn = -1;
            for (int i = 0; i < table.getOutputs().size(); i++) {
                if ("outcome".equals(table.getOutputs().get(i).getName())) outcomeColumn = i;
            }
            if (outcomeColumn < 0 || !"string".equalsIgnoreCase(table.getOutputs().get(outcomeColumn).getTypeRef()))
                throw new IllegalArgumentException("Decision table '" + table.getKey() + "' needs a string output named 'outcome'");
            for (DecisionTableDefinition.Rule rule : table.getRules()) {
                String result = rule.getOutputEntries().get(outcomeColumn).trim();
                if (result.length() < 2 || result.charAt(0) != '\"' || result.charAt(result.length() - 1) != '\"')
                    throw new IllegalArgumentException("Decision table '" + table.getKey() + "' outcome values must be quoted string literals");
                String label = result.substring(1, result.length() - 1);
                if (!reference.getValue().getOutcomes().containsKey(label))
                    throw new IllegalArgumentException("Decision table '" + table.getKey() + "' returns undeclared workflow outcome '" + label + "'");
            }
            String versionedKey = CwmToBpmnCompiler.versionedDecisionKey(reference.getKey(), definition.getMetadata().getVersion());
            String dmn = compiler.compile(table, versionedKey);
            DmnDecision deployed = dmnEngine.getDmnRepositoryService().createDecisionQuery()
                    .decisionKey(versionedKey).latestVersion().singleResult();
            if (deployed != null) {
                try (var input = dmnEngine.getDmnRepositoryService().getResourceAsStream(deployed.getDeploymentId(), deployed.getResourceName())) {
                    String existing = input == null ? "" : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (!dmn.equals(existing)) throw new IllegalStateException("Decision '" + versionedKey + "' changed without a workflow semantic version bump");
                }
            } else {
                dmnEngine.getDmnRepositoryService().createDeployment().name(versionedKey)
                        .addString(versionedKey + ".dmn", dmn).deploy();
            }
        }
    }

    private boolean deploymentMatches(ProcessDefinition processDefinition, String source) {
        try (var input = repository.getResourceAsStream(processDefinition.getDeploymentId(), processDefinition.getResourceName())) {
            return input != null && source.equals(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not compare deployed workflow " + processDefinition.getKey(), e);
        }
    }

    public List<Map<String, Object>> list() {
        return entries.values().stream().sorted(Comparator.comparing(e -> e.definition().getMetadata().getName()))
                .map(this::summary).toList();
    }

    public Map<String, Object> summary(CatalogEntry entry) {
        WorkflowDefinition d = entry.definition();
        List<Map<String, Object>> stages = d.getStages().stream().map(s -> Map.<String, Object>of(
                "id", s.getId(), "name", value(s.getName(), s.getId()), "alternate", s.isAlternate(),
                "resolution", value(s.getResolution(), ""), "steps", s.getSteps().stream().map(this::stepSummary).toList())).toList();
        return Map.of("key", d.getMetadata().getKey(), "name", value(d.getMetadata().getName(), d.getMetadata().getKey()),
                "description", value(d.getMetadata().getDescription(), ""), "version", value(d.getMetadata().getVersion(), "0.1.0"),
                "owner", value(d.getMetadata().getOwner(), ""), "dataClassification", value(d.getMetadata().getDataClassification(), "internal"),
                "roles", d.getRoles(), "stages", stages, "startForm", startForm(entry));
    }

    private Map<String, Object> stepSummary(StepDefinition step) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", step.getId()); result.put("name", value(step.getName(), step.getId()));
        result.put("type", step.getType()); result.put("form", step.getForm());
        result.put("outcomes", step.getOutcomes());
        if (step.getSla() != null) result.put("sla", Map.of("goal", value(step.getSla().getGoal(), ""),
                "deadline", value(step.getSla().getDeadline(), ""), "onDeadline", value(step.getSla().getOnDeadline(), "")));
        result.put("assignee", step.getAssignee() == null ? null : Map.of("role", value(step.getAssignee().getRole(), ""), "user", value(step.getAssignee().getUser(), "")));
        return result;
    }

    public CatalogEntry require(String key) {
        CatalogEntry entry = entries.get(key);
        if (entry == null) throw new NoSuchElementException("Workflow not found: " + key);
        return entry;
    }

    public JsonNode form(CatalogEntry entry, String formPath) {
        if (formPath == null || formPath.isBlank()) return json.createObjectNode().put("title", "No form defined");
        Path packageRoot = entry.file().getParent();
        Path formFile = packageRoot.resolve(formPath).normalize();
        if (!formFile.startsWith(packageRoot)) throw new IllegalArgumentException("Form path escapes workflow folder");
        try { return json.readTree(formFile.toFile()); }
        catch (IOException e) { throw new IllegalArgumentException("Could not read form " + formPath + ": " + e.getMessage(), e); }
    }

    public JsonNode startForm(CatalogEntry entry) {
        for (StageDefinition stage : entry.definition().getStages()) for (StepDefinition step : stage.getSteps())
            if (("userTask".equals(step.getType()) || "approval".equals(step.getType())) && step.getForm() != null)
                return form(entry, step.getForm());
        return json.createObjectNode().put("title", "Start workflow").set("properties", json.createObjectNode());
    }

    public void validateForm(JsonNode schema, Map<String, Object> data) {
        JsonNode values = json.valueToTree(data == null ? Map.of() : data);
        List<String> errors = new ArrayList<>();
        validateObject(schema, values, "", errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Form validation failed: " + String.join("; ", errors));
    }

    public void validateWorkflowData(CatalogEntry entry, Map<String, ?> data) {
        dataSchemas.validate(entry.processDefinitionId(), data);
    }

    public void validateWorkflowData(String processDefinitionId, Map<String, ?> data) {
        dataSchemas.validate(processDefinitionId, data);
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode required = schema.path("required");
        if (required.isArray()) for (JsonNode field : required) {
            String name = field.asText();
            if (!value.has(name) || value.get(name).isNull() || value.get(name).asText().isBlank())
                errors.add((path.isBlank() ? "" : path + ".") + name + " is required");
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject() || !value.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode actual = value.get(field.getKey());
            if (actual == null || actual.isNull()) continue;
            JsonNode rule = field.getValue();
            String fieldPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
            String type = rule.path("type").asText("");
            boolean valid = switch (type) {
                case "string" -> actual.isTextual();
                case "number" -> actual.isNumber();
                case "integer" -> actual.isIntegralNumber();
                case "boolean" -> actual.isBoolean();
                case "array" -> actual.isArray();
                case "object" -> actual.isObject();
                default -> true;
            };
            if (!valid) { errors.add(fieldPath + " must be " + type); continue; }
            if (rule.has("enum")) {
                boolean allowed = false;
                for (JsonNode option : rule.path("enum")) if (option.equals(actual)) { allowed = true; break; }
                if (!allowed) errors.add(fieldPath + " has an unsupported value");
            }
            if (actual.isNumber()) {
                if (rule.has("minimum") && actual.asDouble() < rule.path("minimum").asDouble()) errors.add(fieldPath + " is below its minimum");
                if (rule.has("maximum") && actual.asDouble() > rule.path("maximum").asDouble()) errors.add(fieldPath + " exceeds its maximum");
            }
            if (actual.isTextual() && "email".equals(rule.path("format").asText())
                    && !actual.asText().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) errors.add(fieldPath + " must be a valid email address");
            if (actual.isObject()) validateObject(rule, actual, fieldPath, errors);
        }
    }

    public String stepForm(String workflowKey, String stepId) {
        for (StageDefinition stage : require(workflowKey).definition().getStages()) for (StepDefinition step : stage.getSteps())
            if (step.getId().equals(stepId)) return step.getForm();
        return null;
    }

    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    public record CatalogEntry(WorkflowDefinition definition, Path file, String processDefinitionId) {}
}
