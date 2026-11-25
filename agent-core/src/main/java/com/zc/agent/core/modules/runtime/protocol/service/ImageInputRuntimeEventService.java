package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 发布不包含图片正文的多模态输入路由与分析事实。 */
@Service
public class ImageInputRuntimeEventService {

    private static final Logger log = LoggerFactory.getLogger(ImageInputRuntimeEventService.class);
    private final RuntimeEventPublisher eventPublisher;

    public ImageInputRuntimeEventService(RuntimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 在任何模型或 OCR Provider 调用前冻结附件引用、摘要和选择的路由。 */
    public void resolved(ExecutionContext context, String route, String modelCode) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) return;
        List<Map<String, Object>> attachments = context.messageContext().attachments().stream()
                .map(item -> Map.<String, Object>of(
                        "attachmentId", item.attachmentId(),
                        "attachmentCode", safe(item.attachmentCode()),
                        "mimeType", safe(item.mimeType()),
                        "sizeBytes", item.sizeBytes() == null ? 0L : item.sizeBytes(),
                        "sha256", safe(item.sha256())
                )).toList();
        eventPublisher.publishPayload(runCode, RuntimeEventType.INPUT_ATTACHMENT_RESOLVED,
                Map.of("route", route, "modelCode", safe(modelCode), "attachments", attachments), trace(context));
    }

    /** OCR 已产生外部结果后尽力记录摘要事实，事件失败不得导致 Provider 重复调用。 */
    public void analyzed(ExecutionContext context, ExecutionContext.MessageAttachmentContext attachment,
                         String toolCode, int responseLength) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) return;
        try {
            eventPublisher.publishPayload(runCode, RuntimeEventType.INPUT_ATTACHMENT_ANALYZED,
                    Map.of(
                            "attachmentId", attachment.attachmentId(),
                            "attachmentCode", safe(attachment.attachmentCode()),
                            "mimeType", safe(attachment.mimeType()),
                            "sha256", safe(attachment.sha256()),
                            "toolCode", toolCode,
                            "responseLength", Math.max(0, responseLength)
                    ), trace(context));
        } catch (RuntimeException exception) {
            log.error("图片分析结果事件写入失败，禁止重复调用 Provider, runCode={}, attachmentId={}",
                    runCode, attachment.attachmentId(), exception);
        }
    }

    private Map<String, Object> trace(ExecutionContext context) {
        String traceId = context.taskContext() == null ? null : context.taskContext().traceId();
        return traceId == null || traceId.isBlank() ? Map.of() : Map.of("traceId", traceId);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
