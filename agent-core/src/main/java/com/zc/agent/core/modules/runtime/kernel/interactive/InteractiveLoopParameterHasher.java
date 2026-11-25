package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

/** 对 Tool 结构化参数生成不受 Map 顺序影响的 SHA-256 指纹。 */
@Component
public class InteractiveLoopParameterHasher {

    private final ObjectMapper objectMapper;

    public InteractiveLoopParameterHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 返回规范化参数哈希，原始参数不会离开当前调用边界。 */
    public String hash(Map<String, Object> arguments) {
        JsonNode canonical = canonicalize(objectMapper.valueToTree(arguments == null ? Map.of() : arguments));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        ObjectNode object = objectMapper.createObjectNode();
        java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                .sorted(Comparator.naturalOrder())
                .forEach(field -> object.set(field, canonicalize(node.get(field))));
        return object;
    }
}
