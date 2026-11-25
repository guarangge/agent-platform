package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.attachment.model.AttachmentContent;
import com.zc.agent.core.modules.attachment.service.ConversationAttachmentService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 在 Tool 调用期解析当前消息附件，禁止插件越权读取其他会话附件。 */
@Component
public class ImageToolAttachmentResolver {

    private final ConversationAttachmentService attachmentService;

    public ImageToolAttachmentResolver(ConversationAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** 解析附件引用并读取已经过 SHA-256 校验的正文。 */
    public ResolvedImage resolve(ExecutionContext context, ToolInvocationRequest request) {
        List<ExecutionContext.MessageAttachmentContext> attachments = context.messageContext() == null
                || context.messageContext().attachments() == null ? List.of() : context.messageContext().attachments();
        Long requestedId = parseAttachmentId(request.arguments());
        ExecutionContext.MessageAttachmentContext selected = attachments.stream()
                .filter(item -> requestedId == null || requestedId.equals(item.attachmentId()))
                .findFirst()
                .orElseThrow(() -> failure(requestedId == null ? "当前消息没有可分析的图片" : "附件不属于当前消息"));
        AttachmentContent content = attachmentService.readForRuntime(
                context.conversationContext().conversationId(), selected.attachmentId());
        return new ResolvedImage(selected, content);
    }

    private Long parseAttachmentId(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get("attachmentId");
        if (value == null || value.toString().isBlank()) return null;
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw failure("attachmentId 必须是整数");
        }
    }

    private ToolExecutionException failure(String message) {
        return new ToolExecutionException(message, "IMAGE_ATTACHMENT_INVALID", "validation", false, null, Map.of());
    }

    /** 当前消息中的安全附件引用与临时正文。 */
    public record ResolvedImage(ExecutionContext.MessageAttachmentContext reference, AttachmentContent content) {
    }
}
