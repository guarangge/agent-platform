package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 通过独立 OpenAI-compatible Vision Provider 执行 OCR。 */
@Component
public class ImageOcrToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "IMAGE_OCR";
    private final ImageToolAttachmentResolver attachmentResolver;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final HttpClient httpClient;

    @Autowired
    public ImageOcrToolExecutor(ImageToolAttachmentResolver attachmentResolver,
                                ObjectMapper objectMapper,
                                Environment environment) {
        this(attachmentResolver, objectMapper, environment, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    ImageOcrToolExecutor(ImageToolAttachmentResolver attachmentResolver, ObjectMapper objectMapper,
                         Environment environment, HttpClient httpClient) {
        this.attachmentResolver = attachmentResolver;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, String input) {
        return execute(context, tool, new ToolInvocationRequest(input, Map.of()));
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String endpoint = property("agent.plugin.image.ocr-endpoint");
        String model = property("agent.plugin.image.ocr-model");
        if (endpoint == null || model == null) {
            throw new ToolExecutionException("IMAGE_OCR 尚未配置识别 Provider",
                    "IMAGE_OCR_PROVIDER_NOT_CONFIGURED", "configuration", false, null,
                    Map.of("requiredProperties", "agent.plugin.image.ocr-endpoint,agent.plugin.image.ocr-model"));
        }
        ImageToolAttachmentResolver.ResolvedImage image = attachmentResolver.resolve(context, request);
        String prompt = stringArgument(request, "prompt", "请识别图片中的全部文字，保持自然阅读顺序，只输出识别正文。");
        JsonNode response = invoke(endpoint, model, property("agent.plugin.image.ocr-api-key"), prompt, image);
        String text = response.path("choices").path(0).path("message").path("content").asText("").trim();
        if (text.isBlank()) {
            throw providerFailure("OCR Provider 未返回文本", null);
        }
        int maxChars = intArgument(request, "maxChars", 20000, 100, 100000);
        boolean truncated = text.length() > maxChars;
        String outputText = truncated ? text.substring(0, maxChars) : text;
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachmentId", image.reference().attachmentId());
        payload.put("fileName", image.reference().fileName());
        payload.put("mimeType", image.content().mimeType());
        payload.put("sha256", image.reference().sha256());
        payload.put("text", outputText);
        payload.put("truncated", truncated);
        payload.put("providerModel", model);
        return new ToolExecutionResult(TOOL_CODE, tool.toolName(), "图片文字识别完成", outputText, toJson(payload));
    }

    private JsonNode invoke(String endpoint, String model, String apiKey, String prompt,
                            ImageToolAttachmentResolver.ResolvedImage image) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        ArrayNode content = messages.addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "text").put("text", prompt);
        content.addObject().put("type", "image_url").putObject("image_url").put("url",
                "data:" + image.content().mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.content().bytes()));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (apiKey != null) builder.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerFailure("OCR Provider HTTP 状态异常", response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (ToolExecutionException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerFailure("OCR Provider 调用被中断", null);
        } catch (Exception exception) {
            throw providerFailure("OCR Provider 调用失败", null);
        }
    }

    private String property(String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int timeoutSeconds() {
        return Math.max(5, Math.min(120, environment.getProperty("agent.plugin.image.ocr-timeout-seconds", Integer.class, 60)));
    }

    private String stringArgument(ToolInvocationRequest request, String key, String fallback) {
        Object value = request.arguments() == null ? null : request.arguments().get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private int intArgument(ToolInvocationRequest request, String key, int fallback, int min, int max) {
        Object value = request.arguments() == null ? null : request.arguments().get(key);
        if (value == null) return fallback;
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private ToolExecutionException providerFailure(String message, Integer status) {
        return new ToolExecutionException(message, "IMAGE_OCR_PROVIDER_FAILED", "provider", true, status, Map.of());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OCR 结果序列化失败", exception);
        }
    }
}
