package com.w6w.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Validates process variables against the JSON Schema registered for a workflow. */
@Component
public class WorkflowDataSchemaRegistry {
    private static final Set<String> SUPPORTED_SCHEMA_KEYWORDS = Set.of(
            "$schema", "$id", "title", "description", "default", "type", "required", "properties",
            "additionalProperties", "items", "minLength", "maxLength", "pattern", "format",
            "minimum", "maximum", "minItems", "maxItems", "uniqueItems", "enum");
    private static final Set<String> SUPPORTED_TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();

    public WorkflowDataSchemaRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String workflowKey, JsonNode schema) {
        if (workflowKey == null || workflowKey.isBlank()) throw new IllegalArgumentException("Workflow key is required");
        if (schema == null || !schema.isObject()) throw new IllegalArgumentException("Workflow data schema must be a JSON object");
        validateSchemaDefinition(schema, "$");
        JsonNode candidate = schema.deepCopy();
        JsonNode existing = schemas.putIfAbsent(workflowKey, candidate);
        if (existing != null && !existing.equals(candidate))
            throw new IllegalStateException("Workflow data schema changed without a new workflow deployment version: " + workflowKey);
    }

    public boolean hasSchema(String workflowKey) {
        return schemas.containsKey(workflowKey);
    }

    public void validate(String workflowKey, Map<String, ?> data) {
        JsonNode schema = schemas.get(workflowKey);
        if (schema == null) return;
        JsonNode value = objectMapper.valueToTree(data == null ? Map.of() : data);
        var errors = new ArrayList<String>();
        validateNode(schema, value, "", errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Workflow data validation failed: " + String.join("; ", errors));
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, java.util.List<String> errors) {
        String type = schema.path("type").asText("");
        if (!type.isBlank() && !matchesType(type, value)) {
            errors.add(location(path) + "must be " + type);
            return;
        }

        JsonNode required = schema.path("required");
        if (required.isArray() && value.isObject()) for (JsonNode field : required) {
            String name = field.asText();
            if (!value.has(name) || value.get(name).isNull()) errors.add(location(childPath(path, name)) + "is required");
        }

        JsonNode properties = schema.path("properties");
        if (value.isObject() && properties.isObject()) {
            value.fields().forEachRemaining(entry -> {
                JsonNode childSchema = properties.get(entry.getKey());
                if (childSchema == null) {
                    if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean())
                        errors.add(location(childPath(path, entry.getKey())) + "is not allowed");
                    return;
                }
                validateNode(childSchema, entry.getValue(), childPath(path, entry.getKey()), errors);
            });
        }

        JsonNode items = schema.path("items");
        if (value.isArray() && items.isObject()) for (int i = 0; i < value.size(); i++)
            validateNode(items, value.get(i), path + "[" + i + "]", errors);
        if (value.isArray()) {
            if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) errors.add(location(path) + "has fewer than minItems");
            if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) errors.add(location(path) + "has more than maxItems");
            if (schema.path("uniqueItems").asBoolean(false)) {
                for (int i = 0; i < value.size(); i++) for (int j = i + 1; j < value.size(); j++)
                    if (value.get(i).equals(value.get(j))) { errors.add(location(path) + "contains duplicate items"); i = value.size(); break; }
            }
        }

        if (value.isTextual()) {
            String text = value.asText();
            if (schema.has("minLength") && text.codePointCount(0, text.length()) < schema.path("minLength").asInt()) errors.add(location(path) + "is shorter than minLength");
            if (schema.has("maxLength") && text.codePointCount(0, text.length()) > schema.path("maxLength").asInt()) errors.add(location(path) + "is longer than maxLength");
            if (schema.has("pattern")) {
                try {
                    if (!Pattern.compile(schema.path("pattern").asText()).matcher(text).find()) errors.add(location(path) + "does not match the required pattern");
                } catch (PatternSyntaxException e) {
                    errors.add(location(path) + "has an invalid schema pattern");
                }
            }
            if ("email".equals(schema.path("format").asText()) && !text.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                errors.add(location(path) + "must be a valid email address");
        }

        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) errors.add(location(path) + "is below its minimum");
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) errors.add(location(path) + "exceeds its maximum");
        }
        if (schema.has("enum")) {
            boolean allowed = false;
            for (JsonNode option : schema.path("enum")) if (option.equals(value)) { allowed = true; break; }
            if (!allowed) errors.add(location(path) + "has an unsupported value");
        }
    }

    private void validateSchemaDefinition(JsonNode schema, String path) {
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!SUPPORTED_SCHEMA_KEYWORDS.contains(keyword))
                throw new IllegalArgumentException("Unsupported JSON Schema keyword '" + keyword + "' at " + path);
        });
        if (schema.has("type")) {
            String type = schema.path("type").asText();
            if (!SUPPORTED_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported JSON Schema type '" + type + "' at " + path);
        }
        if (schema.has("required")) {
            if (!schema.path("required").isArray()) throw new IllegalArgumentException("JSON Schema required must be an array at " + path);
            for (JsonNode property : schema.path("required")) if (!property.isTextual())
                throw new IllegalArgumentException("JSON Schema required entries must be strings at " + path);
        }
        if (schema.has("properties")) {
            if (!schema.path("properties").isObject()) throw new IllegalArgumentException("JSON Schema properties must be an object at " + path);
            schema.path("properties").fields().forEachRemaining(entry -> {
                if (!entry.getValue().isObject()) throw new IllegalArgumentException("Boolean JSON Schemas are not supported at " + path + ".properties." + entry.getKey());
                validateSchemaDefinition(entry.getValue(), path + ".properties." + entry.getKey());
            });
        }
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").isBoolean())
            throw new IllegalArgumentException("JSON Schema additionalProperties must be a boolean at " + path);
        if (schema.has("items")) {
            if (!schema.path("items").isObject()) throw new IllegalArgumentException("JSON Schema items must be a schema object at " + path);
            validateSchemaDefinition(schema.path("items"), path + ".items");
        }
        if (schema.has("enum") && !schema.path("enum").isArray()) throw new IllegalArgumentException("JSON Schema enum must be an array at " + path);
        if (schema.has("format") && !"email".equals(schema.path("format").asText()))
            throw new IllegalArgumentException("Unsupported JSON Schema format '" + schema.path("format").asText() + "' at " + path);
        if (schema.has("pattern")) {
            try { Pattern.compile(schema.path("pattern").asText()); }
            catch (PatternSyntaxException e) { throw new IllegalArgumentException("Invalid JSON Schema pattern at " + path, e); }
        }
        for (String keyword : Set.of("minLength", "maxLength", "minItems", "maxItems"))
            if (schema.has(keyword) && (!schema.path(keyword).isIntegralNumber() || schema.path(keyword).asInt() < 0))
                throw new IllegalArgumentException("JSON Schema " + keyword + " must be a non-negative integer at " + path);
        for (String keyword : Set.of("minimum", "maximum"))
            if (schema.has(keyword) && !schema.path(keyword).isNumber())
                throw new IllegalArgumentException("JSON Schema " + keyword + " must be a number at " + path);
        if (schema.has("uniqueItems") && !schema.path("uniqueItems").isBoolean())
            throw new IllegalArgumentException("JSON Schema uniqueItems must be a boolean at " + path);
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private String location(String path) { return path.isBlank() ? "Workflow data " : "Workflow data " + path + " "; }
    private String childPath(String path, String child) { return path.isBlank() ? child : path + "." + child; }
}
