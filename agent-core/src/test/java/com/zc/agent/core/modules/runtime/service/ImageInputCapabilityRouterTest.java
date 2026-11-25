package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.service.ImageInputRuntimeEventService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageInputCapabilityRouterTest {

    private final ToolRouterService toolRouterService = mock(ToolRouterService.class);
    private final ImageInputRuntimeEventService eventService = mock(ImageInputRuntimeEventService.class);
    private final ImageInputCapabilityRouter router = new ImageInputCapabilityRouter(toolRouterService, eventService);

    @Test
    void shouldUseNativeVisionWithoutCallingOcr() {
        ExecutionContext context = contextWithImage(List.of());
        ExecutionContext.ModelContext model = mock(ExecutionContext.ModelContext.class);
        when(model.supportsInputModality("image")).thenReturn(true);
        when(model.modelCode()).thenReturn("vision-model");

        var result = router.prepare(context, "描述图片", model);

        assertThat(result.route()).isEqualTo("native_vision");
        assertThat(result.includeNativeImages()).isTrue();
        verify(toolRouterService, never()).execute(any(), any(), any(), any());
    }

    @Test
    void shouldUseOcrForTextModelAndKeepOnlyTextInPrompt() {
        RegistryAgentTool ocr = mock(RegistryAgentTool.class);
        when(ocr.toolCode()).thenReturn("IMAGE_OCR");
        when(ocr.status()).thenReturn("enabled");
        ExecutionContext context = contextWithImage(List.of(ocr));
        ExecutionContext.ModelContext model = mock(ExecutionContext.ModelContext.class);
        when(model.supportsInputModality("image")).thenReturn(false);
        when(model.modelCode()).thenReturn("text-model");
        when(toolRouterService.execute(eq(context), eq("IMAGE_OCR"), eq("用户问题"), eq(Map.of("attachmentId", 7L))))
                .thenReturn(Optional.of(new ToolExecutionResult("IMAGE_OCR", "OCR", "完成", "识别正文", "{}")));

        var result = router.prepare(context, "回答图片", model);

        assertThat(result.route()).isEqualTo("image_ocr");
        assertThat(result.includeNativeImages()).isFalse();
        assertThat(result.userPrompt()).contains("识别正文").doesNotContain("base64");
        verify(eventService).analyzed(eq(context), any(), eq("IMAGE_OCR"), eq(4));
    }

    @Test
    void shouldExplainMissingCapability() {
        ExecutionContext context = contextWithImage(List.of());
        ExecutionContext.ModelContext model = mock(ExecutionContext.ModelContext.class);
        when(model.supportsInputModality("image")).thenReturn(false);

        assertThatThrownBy(() -> router.prepare(context, "回答图片", model))
                .isInstanceOf(ImageInputCapabilityRouter.ImageInputRoutingException.class)
                .hasMessageContaining("未配置 IMAGE_OCR Tool");
    }

    @Test
    void shouldExplainMissingOcrProvider() {
        RegistryAgentTool ocr = mock(RegistryAgentTool.class);
        when(ocr.toolCode()).thenReturn("IMAGE_OCR");
        when(ocr.status()).thenReturn("enabled");
        ExecutionContext context = contextWithImage(List.of(ocr));
        ExecutionContext.ModelContext model = mock(ExecutionContext.ModelContext.class);
        when(model.supportsInputModality("image")).thenReturn(false);
        when(toolRouterService.execute(eq(context), eq("IMAGE_OCR"), eq("用户问题"), eq(Map.of("attachmentId", 7L))))
                .thenThrow(new ToolExecutionException("missing", "IMAGE_OCR_PROVIDER_NOT_CONFIGURED",
                        "configuration", false, null, Map.of()));

        assertThatThrownBy(() -> router.prepare(context, "回答图片", model))
                .isInstanceOf(ImageInputCapabilityRouter.ImageInputRoutingException.class)
                .hasMessageContaining("尚未配置识别 Provider");
    }

    private ExecutionContext contextWithImage(List<RegistryAgentTool> tools) {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionContext.MessageAttachmentContext attachment = new ExecutionContext.MessageAttachmentContext(
                7L, "ATT_7", "图片.png", "image/png", 120L, "abc123");
        when(context.messageContext()).thenReturn(new ExecutionContext.MessageContext(9L, "用户问题", List.of(attachment)));
        when(context.availableTools()).thenReturn(tools);
        when(context.userMessage()).thenReturn("用户问题");
        return context;
    }
}
