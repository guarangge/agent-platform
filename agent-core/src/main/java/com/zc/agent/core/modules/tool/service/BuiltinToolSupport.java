package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台内置 Tool 的参数读取、序列化和标准异常辅助方法。
 */
final class BuiltinToolSupport {

    private BuiltinToolSupport() {
    }

    static String requiredText(ToolInvocationRequest request, String name) {
        String value = text(request, name, null);
        if (value == null || value.isBlank()) {
            throw failure("缺少必填参数：" + name, "TOOL_ARGUMENT_REQUIRED", Map.of("argument", name));
        }
        return value.trim();
    }

    static String text(ToolInvocationRequest request, String name, String fallback) {
        Object value = request.arguments() == null ? null : request.arguments().get(name);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    static int integer(ToolInvocationRequest request, String name, int fallback, int minimum, int maximum) {
        Object value = request.arguments() == null ? null : request.arguments().get(name);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            return Math.max(minimum, Math.min(maximum, parsed));
        } catch (NumberFormatException exception) {
            throw failure("参数必须是整数：" + name, "TOOL_ARGUMENT_INVALID", Map.of("argument", name));
        }
    }

    static List<String> stringList(ToolInvocationRequest request, String name) {
        Object value = request.arguments() == null ? null : request.arguments().get(name);
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }

    static JsonNode objectConfig(JsonNode value) {
        return value != null && value.isObject() ? value : null;
    }

    static String json(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("Tool 结果序列化失败", "TOOL_OUTPUT_SERIALIZATION_FAILED", Map.of());
        }
    }

    static ToolExecutionException failure(String message, String code, Map<String, Object> metadata) {
        return new ToolExecutionException(message, code, "execution", false, null, metadata);
    }
}
