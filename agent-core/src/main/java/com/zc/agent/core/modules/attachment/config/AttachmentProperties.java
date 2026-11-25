package com.zc.agent.core.modules.attachment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** 会话附件上传与本地存储配置。 */
@Component
@ConfigurationProperties(prefix = "core.attachment")
public class AttachmentProperties {

    private Path storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "agent-attachments");
    private long maxFileBytes = 10 * 1024 * 1024L;
    private Set<String> allowedMimeTypes = new LinkedHashSet<>(Set.of("image/jpeg", "image/png", "image/webp"));

    public Path getStorageRoot() { return storageRoot; }
    public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }
    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public Set<String> getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(Set<String> allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes == null ? new LinkedHashSet<>() : allowedMimeTypes;
    }
}

