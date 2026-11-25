package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Trace 数据脱敏器。
 * 在统一持久化入口处理凭据和超长文本，避免调用方遗漏安全处理。
 */
@Component
public class TraceDataSanitizer {

    static final String REDACTED = "[REDACTED]";
    private static final int MAX_TEXT_LENGTH = 8_000;
    private static final int MAX_ERROR_LENGTH = 2_000;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "proxyauthorization", "apikey", "accesstoken", "refreshtoken",
            "password", "passwd", "secret", "clientsecret", "privatekey", "credential", "credentials"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+)[a-z0-9._~+/=-]+"
    );
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|client[_-]?secret|secret)\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b"
    );

    private final ObjectMapper objectMapper;

    public TraceDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将任意 Trace 快照转换为完成脱敏的 JSON 树。
     */
    public JsonNode sanitize(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        return sanitizeNode(objectMapper.valueToTree(value));
    }

    /**
     * 清洗异常文本，避免凭据和无限长度的第三方响应进入 Trace。
     */
    public String sanitizeError(String value) {
        return sanitizeText(value, MAX_ERROR_LENGTH);
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(field.getKey(), isSensitiveKey(field.getKey())
                        ? TextNode.valueOf(REDACTED)
                        : sanitizeNode(field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(sanitizeNode(item)));
            return result;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(sanitizeText(node.asText(), MAX_TEXT_LENGTH));
        }
        return node.deepCopy();
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.contains(normalized);
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("$1" + REDACTED);
        sanitized = ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll(REDACTED);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength) + "...[TRUNCATED]";
    }
}
