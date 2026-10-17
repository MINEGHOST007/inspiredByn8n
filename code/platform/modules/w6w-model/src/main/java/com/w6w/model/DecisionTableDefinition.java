package com.w6w.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Versioned, human-authored decision table syntax that can be compiled to DMN. */
public class DecisionTableDefinition {
    private String apiVersion;
    private String key;
    private String name;
    private String hitPolicy = "FIRST";
    private List<Input> inputs;
    private List<Output> outputs;
    private List<Rule> rules;

    public void validate() {
        if (!"w6w/decision/v1".equals(apiVersion)) throw new IllegalArgumentException("Decision table apiVersion must be w6w/decision/v1");
        if (key == null || !key.matches("[a-z0-9]+(-[a-z0-9]+)*")) throw new IllegalArgumentException("Decision table key must be kebab-case");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Decision table name is required");
        if (hitPolicy == null || !Set.of("FIRST", "UNIQUE", "ANY").contains(hitPolicy.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("hitPolicy must be FIRST, UNIQUE, or ANY");
        if (inputs == null || inputs.isEmpty() || outputs == null || outputs.isEmpty() || rules == null || rules.isEmpty())
            throw new IllegalArgumentException("Decision table requires at least one input, output, and rule");
        validateIds(inputs.stream().map(Input::getId).toList(), "input");
        validateIds(outputs.stream().map(Output::getId).toList(), "output");
        validateIds(rules.stream().map(Rule::getId).toList(), "rule");
        for (Input input : inputs) if (blank(input.getExpression()) || blank(input.getTypeRef())) throw new IllegalArgumentException("Each decision input requires expression and typeRef");
        for (Output output : outputs) if (blank(output.getName()) || blank(output.getTypeRef())) throw new IllegalArgumentException("Each decision output requires name and typeRef");
        for (Rule rule : rules) {
            if (rule.getInputEntries() == null || rule.getInputEntries().size() != inputs.size()
                    || rule.getOutputEntries() == null || rule.getOutputEntries().size() != outputs.size())
                throw new IllegalArgumentException("Each rule must contain one input entry and output entry per table column");
        }
    }

    private void validateIds(List<String> ids, String kind) {
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            if (blank(id) || !id.matches("[A-Za-z_][A-Za-z0-9_-]*") || !unique.add(id))
                throw new IllegalArgumentException("Decision " + kind + " IDs must be unique XML-safe identifiers");
        }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHitPolicy() { return hitPolicy; }
    public void setHitPolicy(String hitPolicy) { this.hitPolicy = hitPolicy; }
    public List<Input> getInputs() { return inputs; }
    public void setInputs(List<Input> inputs) { this.inputs = inputs; }
    public List<Output> getOutputs() { return outputs; }
    public void setOutputs(List<Output> outputs) { this.outputs = outputs; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }

    public static class Input {
        private String id;
        private String label;
        private String typeRef;
        private String expression;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getTypeRef() { return typeRef; }
        public void setTypeRef(String typeRef) { this.typeRef = typeRef; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
    }
    public static class Output {
        private String id;
        private String name;
        private String typeRef;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTypeRef() { return typeRef; }
        public void setTypeRef(String typeRef) { this.typeRef = typeRef; }
    }
    public static class Rule {
        private String id;
        private List<String> inputEntries;
        private List<String> outputEntries;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getInputEntries() { return inputEntries; }
        public void setInputEntries(List<String> inputEntries) { this.inputEntries = inputEntries; }
        public List<String> getOutputEntries() { return outputEntries; }
        public void setOutputEntries(List<String> outputEntries) { this.outputEntries = outputEntries; }
    }
}
