package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ImageOcrToolExecutorTest {

    @Test
    void shouldFailBeforeReadingAttachmentWhenProviderIsMissing() {
        ImageOcrToolExecutor executor = new ImageOcrToolExecutor(
                mock(ImageToolAttachmentResolver.class), new ObjectMapper(), new MockEnvironment());

        assertThatThrownBy(() -> executor.execute(null, mock(RegistryAgentTool.class), "识别文字"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("尚未配置识别 Provider")
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo("IMAGE_OCR_PROVIDER_NOT_CONFIGURED");
    }
}
