package com.w6w.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDataSchemaRegistryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowDataSchemaRegistry registry = new WorkflowDataSchemaRegistry(json);

    @Test
    void validatesRequiredTypesEnumsBoundsAndNestedProperties() throws Exception {
        registry.register("definition:1", json.readTree("""
                {"type":"object","required":["guest","tier"],"additionalProperties":false,
                 "properties":{"guest":{"type":"object","required":["email"],"properties":{"email":{"type":"string","format":"email"}}},
                 "tier":{"type":"string","enum":["Silver","Gold"]},"amount":{"type":"number","minimum":0.01,"maximum":100}}}
                """));

        registry.validate("definition:1", Map.of("guest", Map.of("email", "guest@example.test"),
                "tier", "Gold", "amount", 50));
        assertThatThrownBy(() -> registry.validate("definition:1", Map.of("guest", Map.of("email", "not-an-email"),
                "tier", "Bronze", "amount", 0, "unexpected", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guest.email must be a valid email address")
                .hasMessageContaining("tier has an unsupported value")
                .hasMessageContaining("amount is below its minimum")
                .hasMessageContaining("unexpected is not allowed");
    }

    @Test
    void doesNotApplySchemaForUnregisteredEngineDefinitions() {
        registry.validate("unregistered:1", Map.of("any", "value"));
    }

    @Test
    void keepsSchemaImmutableForAnAlreadyDeployedDefinition() throws Exception {
        registry.register("definition:1", json.readTree("{\"type\":\"object\"}"));
        assertThatThrownBy(() -> registry.register("definition:1", json.createObjectNode().put("type", "array")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed without a new workflow deployment version");
    }

    @Test
    void rejectsUnsupportedKeywordsInsteadOfSilentlySkippingSchemaRules() throws Exception {
        assertThatThrownBy(() -> registry.register("definition:2", json.readTree("{\"type\":\"object\",\"$ref\":\"#/$defs/value\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported JSON Schema keyword '$ref'");
    }

    @Test
    void validatesArrayCardinalityAndUniqueness() throws Exception {
        registry.register("definition:3", json.readTree("""
                {"type":"object","properties":{"roles":{"type":"array","minItems":1,"uniqueItems":true,"items":{"type":"string"}}}}
                """));
        registry.validate("definition:3", Map.of("roles", java.util.List.of("agent", "manager")));
        assertThatThrownBy(() -> registry.validate("definition:3", Map.of("roles", java.util.List.of("agent", "agent"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains duplicate items");
    }
}
