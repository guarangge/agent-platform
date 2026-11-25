package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.config.ToolSecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Tool 敏感配置加解密服务。
 */
@Service
public class ToolSecretCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String SECRET_PREFIX = "enc:v1:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final ObjectMapper objectMapper;
    private final byte[] secretKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public ToolSecretCryptoService(ObjectMapper objectMapper, ToolSecurityProperties toolSecurityProperties) {
        this.objectMapper = objectMapper;
        this.secretKeyBytes = sha256(toolSecurityProperties.getEncryptionKey());
    }

    public String encryptJsonText(String jsonText) {
        JsonNode jsonNode = parseJson(jsonText);
        if (isEmptyObject(jsonNode)) {
            return jsonText;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("_encrypted", true);
        wrapper.put("cipherText", encryptText(toJson(jsonNode)));
        return toJson(wrapper);
    }

    public JsonNode decryptJsonText(String jsonText) {
        JsonNode jsonNode = parseJson(jsonText);
        if (!jsonNode.path("_encrypted").asBoolean(false)) {
            return jsonNode;
        }
        String cipherText = jsonNode.path("cipherText").asText(null);
        if (cipherText == null || cipherText.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return parseJson(decryptText(cipherText));
    }

    public JsonNode maskJson(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull() || jsonNode.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (jsonNode.isObject()) {
            ObjectNode masked = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                masked.set(entry.getKey(), maskJson(entry.getValue()));
            }
            return masked;
        }
        if (jsonNode.isArray()) {
            var array = objectMapper.createArrayNode();
            jsonNode.forEach(item -> array.add(maskJson(item)));
            return array;
        }
        if (jsonNode.isTextual()) {
            return objectMapper.getNodeFactory().textNode(maskSecret(jsonNode.asText("")));
        }
        return jsonNode.deepCopy();
    }

    /**
     * 加密单个敏感字符串。带版本前缀便于识别历史明文和后续轮换算法。
     *
     * @param plaintext 明文
     * @return 密文
     */
    public String encryptSecret(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (plaintext.startsWith(SECRET_PREFIX)) {
            return plaintext;
        }
        return SECRET_PREFIX + encryptText(plaintext);
    }

    /**
     * 解密单个敏感字符串。未带版本前缀的数据按历史明文兼容读取。
     *
     * @param storedValue 数据库存储值
     * @return 明文
     */
    public String decryptSecret(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        if (!storedValue.startsWith(SECRET_PREFIX)) {
            return storedValue;
        }
        return decryptText(storedValue.substring(SECRET_PREFIX.length()));
    }

    /**
     * 返回敏感字符串的脱敏展示值。
     *
     * @param storedValue 密文或历史明文
     * @return 脱敏值
     */
    public String maskStoredSecret(String storedValue) {
        return maskSecret(decryptSecret(storedValue));
    }

    private boolean isEmptyObject(JsonNode jsonNode) {
        return jsonNode == null || jsonNode.isNull() || (jsonNode.isObject() && jsonNode.isEmpty());
    }

    private JsonNode parseJson(String jsonText) {
        try {
            return objectMapper.readTree(jsonText == null || jsonText.isBlank() ? "{}" : jsonText);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("敏感配置 JSON 格式不合法");
        }
    }

    private String toJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("敏感配置 JSON 序列化失败");
        }
    }

    private String encryptText(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKeyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new BusinessException("敏感配置加密失败");
        }
    }

    private String decryptText(String cipherText) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKeyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException("敏感配置解密失败");
        }
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BusinessException("敏感配置密钥初始化失败");
        }
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 8) {
            return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
