package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool 输入输出 Schema 校验服务。
 */
@Service
public class ToolSchemaValidationService {

    private final ObjectMapper objectMapper;

    public ToolSchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateInput(RegistryAgentTool tool, Map<String, Object> arguments) {
        JsonNode schema = tool.inputSchema();
        if (isEmptySchema(schema)) {
            return;
        }
        JsonNode data = objectMapper.valueToTree(arguments == null ? Map.of() : arguments);
        List<String> errors = new ArrayList<>();
        validateNode("$input", data, schema, errors);
        if (!errors.isEmpty()) {
            throw new ToolExecutionException(
                    "Tool 输入不符合 Schema: " + String.join("；", errors),
                    "TOOL_INPUT_SCHEMA_INVALID",
                    "validation",
                    false,
                    null,
                    Map.of("toolCode", tool.toolCode())
            );
        }
    }

    public void validateOutput(RegistryAgentTool tool, String outputSnapshot) {
        JsonNode schema = tool.outputSchema();
        if (isEmptySchema(schema)) {
            return;
        }
        JsonNode data = parseOutput(outputSnapshot);
        List<String> errors = new ArrayList<>();
        validateNode("$output", data, schema, errors);
        if (!errors.isEmpty()) {
            throw new ToolExecutionException(
                    "Tool 输出不符合 Schema: " + String.join("；", errors),
                    "TOOL_OUTPUT_SCHEMA_INVALID",
                    "validation",
                    false,
                    null,
                    Map.of("toolCode", tool.toolCode())
            );
        }
    }

    private boolean isEmptySchema(JsonNode schema) {
        return schema == null || schema.isNull() || schema.isMissingNode() || (schema.isObject() && schema.isEmpty());
    }

    private JsonNode parseOutput(String outputSnapshot) {
        try {
            if (outputSnapshot == null || outputSnapshot.isBlank()) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(outputSnapshot);
        } catch (Exception exception) {
            return objectMapper.getNodeFactory().textNode(outputSnapshot);
        }
    }

    private void validateNode(String path, JsonNode data, JsonNode schema, List<String> errors) {
        if (schema == null || schema.isNull() || schema.isMissingNode()) {
            return;
        }
        String expectedType = schema.path("type").asText(null);
        if ((expectedType == null || expectedType.isBlank()) && schema.has("properties")) {
            expectedType = "object";
        }
        if ((expectedType == null || expectedType.isBlank()) && schema.has("items")) {
            expectedType = "array";
        }
        if (expectedType != null && !expectedType.isBlank() && !matchesType(data, expectedType)) {
            errors.add(path + " 类型应为 " + expectedType);
            return;
        }
        if (schema.has("enum") && schema.path("enum").isArray()) {
            boolean matched = false;
            for (JsonNode item : schema.path("enum")) {
                if (item.equals(data)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add(path + " 不在允许的枚举范围内");
            }
        }
        if ("object".equals(expectedType) && data.isObject()) {
            validateObject(path, data, schema, errors);
            return;
        }
        if ("array".equals(expectedType) && data.isArray()) {
            JsonNode itemSchema = schema.path("items");
            if (!itemSchema.isMissingNode() && !itemSchema.isNull()) {
                int index = 0;
                for (JsonNode item : data) {
                    validateNode(path + "[" + index + "]", item, itemSchema, errors);
                    index++;
                }
            }
        }
    }

    private void validateObject(String path, JsonNode data, JsonNode schema, List<String> errors) {
        Set<String> required = new HashSet<>();
        if (schema.path("required").isArray()) {
            schema.path("required").forEach(item -> required.add(item.asText("")));
        }
        required.stream()
                .filter(name -> !name.isBlank())
                .filter(name -> !data.has(name) || data.path(name).isNull())
                .forEach(name -> errors.add(path + "." + name + " 为必填项"));
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (data.has(entry.getKey())) {
                    validateNode(path + "." + entry.getKey(), data.path(entry.getKey()), entry.getValue(), errors);
                }
            }
        }
        if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean(true) && properties.isObject()) {
            Set<String> allowed = new HashSet<>();
            properties.fieldNames().forEachRemaining(allowed::add);
            data.fieldNames().forEachRemaining(name -> {
                if (!allowed.contains(name)) {
                    errors.add(path + "." + name + " 不允许出现");
                }
            });
        }
    }

    private boolean matchesType(JsonNode data, String expectedType) {
        return switch (expectedType) {
            case "object" -> data != null && data.isObject();
            case "array" -> data != null && data.isArray();
            case "string" -> data != null && data.isTextual();
            case "boolean" -> data != null && data.isBoolean();
            case "integer" -> data != null && data.isIntegralNumber();
            case "number" -> data != null && data.isNumber();
            case "null" -> data == null || data.isNull();
            default -> true;
        };
    }
}
