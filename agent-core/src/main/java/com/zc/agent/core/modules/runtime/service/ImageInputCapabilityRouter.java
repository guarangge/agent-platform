package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.service.ImageInputRuntimeEventService;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 根据模型输入模态和当前 Agent Tool 范围确定图片处理路径。 */
@Service
public class ImageInputCapabilityRouter {

    static final String ROUTE_TEXT_ONLY = "text_only";
    static final String ROUTE_NATIVE_VISION = "native_vision";
    static final String ROUTE_IMAGE_OCR = "image_ocr";
    private static final String OCR_TOOL_CODE = "IMAGE_OCR";

    private final ToolRouterService toolRouterService;
    private final ImageInputRuntimeEventService eventService;

    public ImageInputCapabilityRouter(ToolRouterService toolRouterService,
                                      ImageInputRuntimeEventService eventService) {
        this.toolRouterService = toolRouterService;
        this.eventService = eventService;
    }

    /** 视觉模型直读图片；非视觉模型确定性调用已授权 OCR Tool。 */
    public PreparedImageInput prepare(ExecutionContext context, String userPrompt,
                                      ExecutionContext.ModelContext model) {
        List<ExecutionContext.MessageAttachmentContext> attachments = attachments(context);
        if (attachments.isEmpty()) return new PreparedImageInput(userPrompt, ROUTE_TEXT_ONLY, false);
        if (model.supportsInputModality("image")) {
            eventService.resolved(context, ROUTE_NATIVE_VISION, model.modelCode());
            return new PreparedImageInput(userPrompt, ROUTE_NATIVE_VISION, true);
        }
        if (!hasOcrTool(context)) {
            throw new ImageInputRoutingException("当前回复模型不支持图片输入，且 Agent 未配置 IMAGE_OCR Tool。请为模型启用 image 模态，或给 Agent 添加图片文字识别 Tool。");
        }
        eventService.resolved(context, ROUTE_IMAGE_OCR, model.modelCode());
        List<String> recognizedTexts = new ArrayList<>();
        for (ExecutionContext.MessageAttachmentContext attachment : attachments) {
            ToolExecutionResult result;
            try {
                result = toolRouterService.execute(context, OCR_TOOL_CODE, context.userMessage(),
                                Map.of("attachmentId", attachment.attachmentId()))
                        .orElseThrow(() -> new ImageInputRoutingException("IMAGE_OCR Tool 当前不可用，请检查插件部署和 Agent Tool 范围。"));
            } catch (ToolExecutionException exception) {
                if ("IMAGE_OCR_PROVIDER_NOT_CONFIGURED".equals(exception.errorCode())) {
                    throw new ImageInputRoutingException("IMAGE_OCR Tool 已启用，但尚未配置识别 Provider。请配置 endpoint、model 和 API Key。", exception);
                }
                throw exception;
            }
            String text = result.responseText() == null ? "" : result.responseText().trim();
            recognizedTexts.add("附件 " + attachment.fileName() + "：\n" + text);
            eventService.analyzed(context, attachment, OCR_TOOL_CODE, text.length());
        }
        String augmentedPrompt = userPrompt + "\n\n以下是当前消息图片经已授权 OCR Tool 提取的事实，请结合用户问题回答：\n"
                + String.join("\n\n", recognizedTexts);
        return new PreparedImageInput(augmentedPrompt, ROUTE_IMAGE_OCR, false);
    }

    private boolean hasOcrTool(ExecutionContext context) {
        return context.availableTools() != null && context.availableTools().stream()
                .anyMatch(tool -> OCR_TOOL_CODE.equals(tool.toolCode()) && "enabled".equalsIgnoreCase(tool.status()));
    }

    private List<ExecutionContext.MessageAttachmentContext> attachments(ExecutionContext context) {
        return context == null || context.messageContext() == null || context.messageContext().attachments() == null
                ? List.of() : context.messageContext().attachments();
    }

    /** 路由后的 Prompt 与是否允许附加原图。 */
    public record PreparedImageInput(String userPrompt, String route, boolean includeNativeImages) {
    }

    /** 可直接展示给用户的图片能力配置错误。 */
    public static class ImageInputRoutingException extends RuntimeException {
        public ImageInputRoutingException(String message) {
            super(message);
        }

        public ImageInputRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
