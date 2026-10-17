package com.w6w.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class CwmParser {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static WorkflowDefinition parse(File file) throws IOException {
        return YAML_MAPPER.readValue(file, WorkflowDefinition.class);
    }

    public static WorkflowDefinition parse(InputStream inputStream) throws IOException {
        return YAML_MAPPER.readValue(inputStream, WorkflowDefinition.class);
    }

    public static WorkflowDefinition parse(String yamlContent) throws IOException {
        return YAML_MAPPER.readValue(yamlContent, WorkflowDefinition.class);
    }
}
