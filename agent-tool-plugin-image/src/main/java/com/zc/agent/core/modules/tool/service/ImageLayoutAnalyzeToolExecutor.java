package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 本地读取图片头信息并返回基础版面特征，不调用外部服务。 */
@Component
public class ImageLayoutAnalyzeToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "IMAGE_LAYOUT_ANALYZE";
    private final ImageToolAttachmentResolver attachmentResolver;
    private final ObjectMapper objectMapper;

    public ImageLayoutAnalyzeToolExecutor(ImageToolAttachmentResolver attachmentResolver, ObjectMapper objectMapper) {
        this.attachmentResolver = attachmentResolver;
        this.objectMapper = objectMapper;
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
        ImageToolAttachmentResolver.ResolvedImage image = attachmentResolver.resolve(context, request);
        Dimension dimension = readDimension(image.content().bytes(), image.content().mimeType());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachmentId", image.reference().attachmentId());
        payload.put("fileName", image.reference().fileName());
        payload.put("mimeType", image.content().mimeType());
        payload.put("sha256", image.reference().sha256());
        payload.put("width", dimension.width());
        payload.put("height", dimension.height());
        payload.put("orientation", dimension.width() == dimension.height() ? "square"
                : dimension.width() > dimension.height() ? "landscape" : "portrait");
        payload.put("aspectRatio", Math.round((double) dimension.width() / dimension.height() * 1000D) / 1000D);
        String snapshot = toJson(payload);
        return new ToolExecutionResult(TOOL_CODE, tool.toolName(), "图片基础版面分析完成",
                "图片尺寸为 " + dimension.width() + "×" + dimension.height() + "，方向为 " + payload.get("orientation"), snapshot);
    }

    private Dimension readDimension(byte[] bytes, String mimeType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) return new Dimension(image.getWidth(), image.getHeight());
            if ("image/webp".equals(mimeType)) return readWebpDimension(bytes);
            throw invalidImage();
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private Dimension readWebpDimension(byte[] bytes) {
        if (bytes.length >= 30 && "VP8X".equals(new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
            int width = 1 + unsigned24(bytes, 24);
            int height = 1 + unsigned24(bytes, 27);
            return new Dimension(width, height);
        }
        throw new ToolExecutionException("当前 WebP 编码无法读取尺寸，请配置图片分析 Provider",
                "IMAGE_LAYOUT_UNSUPPORTED", "validation", false, null, Map.of("mimeType", "image/webp"));
    }

    private int unsigned24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private ToolExecutionException invalidImage() {
        return new ToolExecutionException("无法读取图片版面信息", "IMAGE_LAYOUT_INVALID", "validation", false, null, Map.of());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("图片版面结果序列化失败", exception);
        }
    }

    private record Dimension(int width, int height) {
    }
}
