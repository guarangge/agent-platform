package com.zc.agent.core.modules.attachment.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.attachment.config.AttachmentProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 图片附件签名和大小校验器，避免只信任客户端扩展名与 Content-Type。 */
@Component
public class ImageAttachmentValidator {

    private final AttachmentProperties properties;

    public ImageAttachmentValidator(AttachmentProperties properties) {
        this.properties = properties;
    }

    /** 校验图片正文并返回按文件签名识别的 MIME。 */
    public String validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("上传文件不能为空");
        }
        if (bytes.length > properties.getMaxFileBytes()) {
            throw new BusinessException("上传文件超过大小限制，最大允许 " + properties.getMaxFileBytes() + " bytes");
        }
        String mimeType = detectMimeType(bytes);
        boolean allowed = properties.getAllowedMimeTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(mimeType::equals);
        if (!allowed) {
            throw new BusinessException("不支持的图片格式，仅允许 JPEG、PNG、WebP");
        }
        return mimeType;
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 3 && unsigned(bytes[0]) == 0xFF && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && unsigned(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && unsigned(bytes[4]) == 0x0D && unsigned(bytes[5]) == 0x0A
                && unsigned(bytes[6]) == 0x1A && unsigned(bytes[7]) == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 12
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private int unsigned(byte value) { return value & 0xFF; }
}
