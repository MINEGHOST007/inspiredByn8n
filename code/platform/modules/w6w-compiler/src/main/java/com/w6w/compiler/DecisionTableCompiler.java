package com.w6w.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.w6w.model.DecisionTableDefinition;

import java.io.IOException;
import java.nio.file.Path;

/** Deterministically compiles W6W decision table YAML into standard DMN XML. */
public class DecisionTableCompiler {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public DecisionTableDefinition parse(Path source) throws IOException {
        DecisionTableDefinition table = yaml.readValue(source.toFile(), DecisionTableDefinition.class);
        table.validate();
        return table;
    }

    public String compile(DecisionTableDefinition table) {
        table.validate();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<definitions xmlns=\"http://www.omg.org/spec/DMN/20151101\" ")
                .append("id=\"definitions_").append(escape(table.getKey())).append("\" name=\"W6W Decisions\" ")
                .append("namespace=\"http://www.flowable.org/dmn\">\n")
                .append("  <decision id=\"").append(escape(table.getKey())).append("\" name=\"").append(escape(table.getName())).append("\">\n")
                .append("    <decisionTable id=\"table_").append(escape(table.getKey())).append("\" hitPolicy=\"")
                .append(table.getHitPolicy().toUpperCase()).append("\">\n");
        for (DecisionTableDefinition.Input input : table.getInputs()) {
            xml.append("      <input id=\"").append(escape(input.getId())).append("\" label=\"").append(escape(input.getLabel() == null ? input.getExpression() : input.getLabel())).append("\">\n")
                    .append("        <inputExpression id=\"inputExpression_").append(escape(input.getId())).append("\" typeRef=\"").append(escape(input.getTypeRef())).append("\"><text>")
                    .append(escape(input.getExpression())).append("</text></inputExpression>\n      </input>\n");
        }
        for (DecisionTableDefinition.Output output : table.getOutputs()) {
            xml.append("      <output id=\"").append(escape(output.getId())).append("\" name=\"").append(escape(output.getName()))
                    .append("\" typeRef=\"").append(escape(output.getTypeRef())).append("\" />\n");
        }
        for (DecisionTableDefinition.Rule rule : table.getRules()) {
            xml.append("      <rule id=\"").append(escape(rule.getId())).append("\">\n");
            for (int i = 0; i < rule.getInputEntries().size(); i++)
                xml.append("        <inputEntry id=\"inputEntry_").append(escape(rule.getId())).append('_').append(i + 1).append("\"><text>")
                        .append(escape(rule.getInputEntries().get(i))).append("</text></inputEntry>\n");
            for (int i = 0; i < rule.getOutputEntries().size(); i++)
                xml.append("        <outputEntry id=\"outputEntry_").append(escape(rule.getId())).append('_').append(i + 1).append("\"><text>")
                        .append(escape(rule.getOutputEntries().get(i))).append("</text></outputEntry>\n");
            xml.append("      </rule>\n");
        }
        return xml.append("    </decisionTable>\n  </decision>\n</definitions>\n").toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
