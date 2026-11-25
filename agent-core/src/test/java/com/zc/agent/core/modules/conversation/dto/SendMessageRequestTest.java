package com.zc.agent.core.modules.conversation.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SendMessageRequestTest {

    @Test
    void shouldKeepTextRequestCompatibleAndNormalizeAttachmentIds() {
        SendMessageRequest textRequest = new SendMessageRequest(" 你好 ");
        SendMessageRequest imageRequest = new SendMessageRequest("", List.of(3L, 3L, 5L));

        assertThat(textRequest.hasInput()).isTrue();
        assertThat(textRequest.normalizedContent()).isEqualTo("你好");
        assertThat(imageRequest.hasInput()).isTrue();
        assertThat(imageRequest.normalizedAttachmentIds()).containsExactly(3L, 5L);
        assertThat(imageRequest.normalizedContent()).isEqualTo("请分析上传的图片");
    }

    @Test
    void shouldRejectRequestWithoutTextAndValidAttachment() {
        assertThat(new SendMessageRequest("  ", List.of()).hasInput()).isFalse();
    }
}
