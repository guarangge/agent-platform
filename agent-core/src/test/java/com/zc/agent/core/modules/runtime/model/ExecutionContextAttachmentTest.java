package com.zc.agent.core.modules.runtime.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextAttachmentTest {

    @Test
    void shouldKeepTextContextCompatibleAndExposeAttachmentMetadata() {
        ExecutionContext.MessageContext text = new ExecutionContext.MessageContext(1L, "文本");
        ExecutionContext.MessageAttachmentContext attachment = new ExecutionContext.MessageAttachmentContext(
                10L, "ATT_10", "截图.png", "image/png", 128L, "abc");
        ExecutionContext.MessageContext multimodal = new ExecutionContext.MessageContext(
                2L, "分析截图", List.of(attachment));

        assertThat(text.attachments()).isEmpty();
        assertThat(multimodal.attachments()).containsExactly(attachment);
        assertThat(multimodal.attachments().get(0).fileName()).isEqualTo("截图.png");
    }
}
