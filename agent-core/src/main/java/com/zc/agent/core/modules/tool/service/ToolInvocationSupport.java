package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool 调用辅助工具。
 */
public final class ToolInvocationSupport {

    private ToolInvocationSupport() {
    }

    public static Map<String, Object> resolveArguments(RegistryAgentTool tool,
                                                       ToolInvocationRequest request,
                                                       ObjectMapper objectMapper) {
        Map<String, Object> baseArguments = new LinkedHashMap<>();
        if (request.arguments() != null) {
            baseArguments.putAll(request.arguments());
        }
        if ((request.arguments() == null || request.arguments().isEmpty())
                && request.toolInputText() != null
                && !request.toolInputText().isBlank()) {
            baseArguments.put("inputText", request.toolInputText());
        }
        JsonNode mappingNode = tool.argumentMapping();
        if (mappingNode == null || mappingNode.isMissingNode() || mappingNode.isNull() || !mappingNode.isObject()) {
            return baseArguments;
        }
        JsonNode renameNode = mappingNode.path("rename");
        if (renameNode.isObject()) {
            Map<String, Object> renamedArguments = new LinkedHashMap<>(baseArguments);
            Iterator<Map.Entry<String, JsonNode>> fields = renameNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String sourceKey = entry.getKey();
                String targetKey = entry.getValue().asText(null);
                if (targetKey == null || targetKey.isBlank() || !renamedArguments.containsKey(sourceKey)) {
                    continue;
                }
                Object value = renamedArguments.remove(sourceKey);
                renamedArguments.put(targetKey, value);
            }
            baseArguments = renamedArguments;
        }
        JsonNode defaultsNode = mappingNode.path("defaults");
        if (defaultsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> defaultFields = defaultsNode.fields();
            while (defaultFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = defaultFields.next();
                baseArguments.putIfAbsent(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
            }
        }
        enrichTextArgumentsFromSchema(tool, request, baseArguments);
        return baseArguments;
    }

    private static void enrichTextArgumentsFromSchema(RegistryAgentTool tool,
                                                      ToolInvocationRequest request,
                                                      Map<String, Object> baseArguments) {
        if (request.toolInputText() == null || request.toolInputText().isBlank()) {
            return;
        }
        JsonNode schema = tool.inputSchema();
        if (schema == null || schema.isNull() || schema.isMissingNode() || !schema.isObject()) {
            return;
        }
        JsonNode propertiesNode = schema.path("properties");
        if (!propertiesNode.isObject()) {
            return;
        }
        List<String> candidateKeys = List.of("query", "content", "message", "text", "prompt");
        for (String candidateKey : candidateKeys) {
            if (baseArguments.containsKey(candidateKey)) {
                continue;
            }
            JsonNode propertySchema = propertiesNode.path(candidateKey);
            if (propertySchema.isMissingNode() || propertySchema.isNull()) {
                continue;
            }
            String propertyType = propertySchema.path("type").asText("");
            if (propertyType.isBlank() || "string".equals(propertyType)) {
                baseArguments.put(candidateKey, request.toolInputText());
            }
        }
    }

    public static Map<String, String> resolveHeaders(RegistryAgentTool tool, ObjectMapper objectMapper) {
        Map<String, String> headers = new LinkedHashMap<>();
        EnvironmentPlaceholderResolver placeholderResolver = new EnvironmentPlaceholderResolver();
        JsonNode headersNode = tool.headers();
        if (headersNode != null && headersNode.isObject()) {
            headersNode.fields().forEachRemaining(entry -> headers.put(
                    entry.getKey(), placeholderResolver.resolve(entry.getValue().asText(""))));
        }
        String authType = tool.authType();
        JsonNode authConfig = tool.authConfig();
        if (authType == null || authType.isBlank() || "none".equals(authType) || authConfig == null || !authConfig.isObject()) {
            return headers;
        }
        switch (authType) {
            case "bearer" -> headers.put("Authorization", "Bearer "
                    + placeholderResolver.resolve(authConfig.path("token").asText("")));
            case "api_key" -> {
                String keyName = authConfig.path("headerName").asText("X-API-Key");
                headers.put(keyName, placeholderResolver.resolve(authConfig.path("apiKey").asText("")));
            }
            case "basic" -> {
                String username = placeholderResolver.resolve(authConfig.path("username").asText(""));
                String password = placeholderResolver.resolve(authConfig.path("password").asText(""));
                String value = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + value);
            }
            case "custom_header" -> headers.put(authConfig.path("headerName").asText("X-Custom-Auth"),
                    placeholderResolver.resolve(authConfig.path("headerValue").asText("")));
            default -> {
            }
        }
        return headers;
    }

    public static String resolveRequestBody(RegistryAgentTool tool,
                                            Map<String, Object> arguments,
                                            ToolInvocationRequest request,
                                            ObjectMapper objectMapper) {
        ObjectNode body = objectMapper.createObjectNode();
        JsonNode invokeConfig = tool.invokeConfig();
        if (invokeConfig != null && invokeConfig.path("bodyTemplate").isObject()) {
            body.setAll((ObjectNode) invokeConfig.path("bodyTemplate").deepCopy());
        }
        body.set("arguments", objectMapper.valueToTree(arguments));
        if (request.toolInputText() != null && !request.toolInputText().isBlank()) {
            body.put("inputText", request.toolInputText());
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Tool 请求体序列化失败");
        }
    }

    public static String readJsonText(ObjectMapper objectMapper, String rawJson, String pointer, String fallback) {
        if (rawJson == null || rawJson.isBlank()) {
            return fallback;
        }
        if (pointer == null || pointer.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode target = resolvePath(root, pointer);
            if (target == null || target.isMissingNode() || target.isNull()) {
                return fallback;
            }
            return target.isValueNode() ? target.asText(fallback) : objectMapper.writeValueAsString(target);
        } catch (Exception exception) {
            return fallback;
        }
    }

    public static JsonNode resolvePath(JsonNode root, String pointer) {
        if (root == null || pointer == null || pointer.isBlank()) {
            return null;
        }
        if (pointer.startsWith("/")) {
            return root.at(pointer);
        }
        String[] segments = pointer.split("\\.");
        JsonNode current = root;
        for (String segment : segments) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return current;
            }
            current = current.path(segment);
        }
        return current;
    }
}
