package com.zc.agent.core.modules.attachment.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.attachment.model.AttachmentContent;
import com.zc.agent.core.modules.attachment.model.ConversationAttachmentRecord;
import com.zc.agent.core.modules.attachment.repository.ConversationAttachmentRepository;
import com.zc.agent.core.modules.attachment.vo.AttachmentResponse;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;

/** 会话附件上传和受权下载服务。 */
@Service
public class ConversationAttachmentService {

    private final ConversationRepository conversationRepository;
    private final ConversationAttachmentRepository attachmentRepository;
    private final AttachmentStorage attachmentStorage;
    private final ImageAttachmentValidator imageAttachmentValidator;

    public ConversationAttachmentService(ConversationRepository conversationRepository,
                                         ConversationAttachmentRepository attachmentRepository,
                                         AttachmentStorage attachmentStorage,
                                         ImageAttachmentValidator imageAttachmentValidator) {
        this.conversationRepository = conversationRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorage = attachmentStorage;
        this.imageAttachmentValidator = imageAttachmentValidator;
    }

    /** 上传图片并保存附件元数据。 */
    public AttachmentResponse upload(Long conversationId, MultipartFile file, SecurityUser user) {
        requireOwnedConversation(conversationId, user);
        byte[] bytes = readBytes(file);
        String mimeType = imageAttachmentValidator.validate(bytes);
        String attachmentCode = "ATT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String filename = sanitizeFilename(file == null ? null : file.getOriginalFilename(), mimeType);
        String storageKey = buildStorageKey(user, conversationId, attachmentCode, mimeType);
        String sha256 = sha256(bytes);
        try {
            attachmentStorage.store(storageKey, bytes);
            Long attachmentId = attachmentRepository.insertReadyInput(
                    conversationId, attachmentCode, filename, mimeType, bytes.length, sha256,
                    attachmentStorage.providerCode(), storageKey, user);
            ConversationAttachmentRecord record = attachmentRepository.findOwnedById(
                    conversationId, attachmentId, user.userId())
                    .orElseThrow(() -> new IllegalStateException("附件元数据写入后无法读取"));
            return AttachmentResponse.from(record);
        } catch (IOException exception) {
            compensateStorage(storageKey);
            throw new BusinessException("附件存储失败", exception);
        } catch (RuntimeException exception) {
            compensateStorage(storageKey);
            throw exception;
        }
    }

    /** 读取当前用户有权访问的附件正文。 */
    public AttachmentContent download(Long conversationId, Long attachmentId, SecurityUser user) {
        requireOwnedConversation(conversationId, user);
        ConversationAttachmentRecord record = attachmentRepository.findOwnedById(
                conversationId, attachmentId, user.userId())
                .orElseThrow(() -> new BusinessException("附件不存在或无权访问"));
        if (!attachmentStorage.providerCode().equals(record.storageProvider())) {
            throw new BusinessException("当前节点未配置附件存储实现：" + record.storageProvider());
        }
        try {
            byte[] content = attachmentStorage.read(record.storageKey());
            if (!record.sha256().equals(sha256(content))) {
                throw new BusinessException("附件内容完整性校验失败");
            }
            return new AttachmentContent(record.originalFilename(), record.mimeType(), content);
        } catch (IOException exception) {
            throw new BusinessException("附件读取失败", exception);
        }
    }

    /** 运行内核读取已绑定附件，调用期校验存储实现与 SHA-256。 */
    public AttachmentContent readForRuntime(Long conversationId, Long attachmentId) {
        ConversationAttachmentRecord record = attachmentRepository.findReadyRuntimeAttachment(conversationId, attachmentId)
                .orElseThrow(() -> new BusinessException("运行时附件不存在或尚未绑定消息"));
        if (!attachmentStorage.providerCode().equals(record.storageProvider())) {
            throw new BusinessException("当前节点未配置附件存储实现：" + record.storageProvider());
        }
        try {
            byte[] content = attachmentStorage.read(record.storageKey());
            if (!record.sha256().equals(sha256(content))) {
                throw new BusinessException("运行时附件内容完整性校验失败");
            }
            return new AttachmentContent(record.originalFilename(), record.mimeType(), content);
        } catch (IOException exception) {
            throw new BusinessException("运行时附件读取失败", exception);
        }
    }

    /** 将附件原子预留给异步 Run，拒绝跨会话、跨用户或重复引用。 */
    public void reserveForRun(Long conversationId, List<Long> attachmentIds, String runCode, SecurityUser user) {
        requireOwnedConversation(conversationId, user);
        List<Long> normalizedIds = normalizeIds(attachmentIds);
        if (normalizedIds.isEmpty()) return;
        int updated = attachmentRepository.reserveForRun(conversationId, normalizedIds, runCode, user.userId());
        if (updated != normalizedIds.size()) {
            throw new BusinessException("部分附件不存在、已被使用或不属于当前会话");
        }
    }

    /** 将附件绑定到已落库的用户消息。 */
    public void bindToMessage(Long conversationId, List<Long> attachmentIds, String runCode,
                              Long messageId, SecurityUser user) {
        requireOwnedConversation(conversationId, user);
        if (runCode != null && !runCode.isBlank()) {
            attachmentRepository.bindRunToMessage(conversationId, runCode, messageId, user.userId());
            return;
        }
        List<Long> normalizedIds = normalizeIds(attachmentIds);
        if (normalizedIds.isEmpty()) return;
        int updated = attachmentRepository.bindToMessage(
                conversationId, normalizedIds, messageId, user.userId());
        if (updated != normalizedIds.size()) {
            throw new BusinessException("部分附件不存在、已被使用或不属于当前会话");
        }
    }

    /** 查询指定消息的附件安全响应。 */
    public List<AttachmentResponse> listMessageAttachments(Long conversationId, Long messageId) {
        return attachmentRepository.findReadyByMessageId(conversationId, messageId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    /** 删除尚未进入消息或Run的附件，并尽力清理存储正文。 */
    public void deleteUnbound(Long conversationId, Long attachmentId, SecurityUser user) {
        requireOwnedConversation(conversationId, user);
        ConversationAttachmentRecord record = attachmentRepository.findOwnedById(
                conversationId, attachmentId, user.userId())
                .orElseThrow(() -> new BusinessException("附件不存在或无权访问"));
        if (attachmentRepository.deleteUnbound(conversationId, attachmentId, user.userId()) != 1) {
            throw new BusinessException("附件已被消息使用，不能删除");
        }
        if (attachmentStorage.providerCode().equals(record.storageProvider())) {
            compensateStorage(record.storageKey());
        }
    }

    private List<Long> normalizeIds(List<Long> attachmentIds) {
        if (attachmentIds == null) return List.of();
        return attachmentIds.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private void requireOwnedConversation(Long conversationId, SecurityUser user) {
        if (conversationId == null || user == null || user.userId() == null
                || conversationRepository.findConversationById(conversationId, user.userId()).isEmpty()) {
            throw new BusinessException("会话不存在或无权访问");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败", exception);
        }
    }

    private String sanitizeFilename(String originalFilename, String mimeType) {
        String normalized = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String filename = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        filename = filename.replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isBlank()) {
            filename = "image." + extension(mimeType);
        }
        if (filename.length() > 255) {
            filename = filename.substring(0, 240) + "." + extension(mimeType);
        }
        return filename;
    }

    private String buildStorageKey(SecurityUser user, Long conversationId, String attachmentCode, String mimeType) {
        return scopeValue(user.tenantId()) + "/" + scopeValue(user.projectId()) + "/"
                + conversationId + "/" + attachmentCode + "." + extension(mimeType);
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    private long scopeValue(Long value) { return value == null ? 1L : value; }

    private void compensateStorage(String storageKey) {
        try {
            attachmentStorage.delete(storageKey);
        } catch (IOException ignored) {
            // 元数据写入失败时尽力清理；存储巡检任务可继续处理极少量残留文件。
        }
    }
}
