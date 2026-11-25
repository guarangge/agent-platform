package com.zc.agent.core.modules.attachment.service;

import com.zc.agent.core.modules.attachment.config.AttachmentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 开发环境本地附件存储实现。 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path storageRoot;
    private final long maxFileBytes;

    public LocalAttachmentStorage(AttachmentProperties properties) {
        this.storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
        this.maxFileBytes = properties.getMaxFileBytes();
    }

    @Override
    public String providerCode() { return "local"; }

    @Override
    public void store(String storageKey, byte[] content) throws IOException {
        Path target = resolveSafely(storageKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public byte[] read(String storageKey) throws IOException {
        Path target = resolveSafely(storageKey);
        if (Files.size(target) > maxFileBytes) {
            throw new IOException("附件正文超过读取大小限制");
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveSafely(storageKey));
    }

    private Path resolveSafely(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("附件 storageKey 不能为空");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("附件 storageKey 不安全");
        }
        return resolved;
    }
}
