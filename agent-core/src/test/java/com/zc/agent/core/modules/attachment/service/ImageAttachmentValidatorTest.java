package com.zc.agent.core.modules.attachment.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageAttachmentValidatorTest {

    private final AttachmentProperties properties = new AttachmentProperties();
    private final ImageAttachmentValidator validator = new ImageAttachmentValidator(properties);

    @Test
    void shouldDetectSupportedImageFromMagicBytes() {
        assertThat(validator.validate(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .isEqualTo("image/jpeg");
        assertThat(validator.validate(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .isEqualTo("image/png");
        assertThat(validator.validate(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}))
                .isEqualTo("image/webp");
    }

    @Test
    void shouldRejectUnknownOrOversizedContent() {
        assertThatThrownBy(() -> validator.validate("not-image".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的图片格式");

        properties.setMaxFileBytes(3);
        assertThatThrownBy(() -> validator.validate(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过大小限制");
    }
}
