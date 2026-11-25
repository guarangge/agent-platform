package com.zc.agent.core.modules.attachment.repository;

import com.zc.agent.core.modules.attachment.model.ConversationAttachmentRecord;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

/** 会话附件元数据仓储。 */
@Repository
public class ConversationAttachmentRepository {

    private static final RowMapper<ConversationAttachmentRecord> ROW_MAPPER = (resultSet, rowNum) ->
            new ConversationAttachmentRecord(
                    resultSet.getLong("id"), resultSet.getString("attachment_code"),
                    resultSet.getLong("conversation_id"), resultSet.getObject("message_id", Long.class),
                    resultSet.getString("run_code"), resultSet.getString("direction"),
                    resultSet.getString("original_filename"), resultSet.getString("mime_type"),
                    resultSet.getLong("size_bytes"), resultSet.getString("sha256"),
                    resultSet.getString("storage_provider"), resultSet.getString("storage_key"),
                    resultSet.getString("status"), resultSet.getTimestamp("created_at").toLocalDateTime());

    private static final String SELECT_COLUMNS = """
            SELECT attachment.id, attachment.attachment_code, attachment.conversation_id,
                   attachment.message_id, attachment.run_code, attachment.direction,
                   attachment.original_filename, attachment.mime_type, attachment.size_bytes,
                   attachment.sha256, attachment.storage_provider, attachment.storage_key,
                   attachment.status, attachment.created_at
            FROM conversation_attachments attachment
            JOIN conversations conversation ON conversation.id = attachment.conversation_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public ConversationAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 新增已完成存储的输入附件元数据。 */
    public Long insertReadyInput(Long conversationId,
                                 String attachmentCode,
                                 String originalFilename,
                                 String mimeType,
                                 long sizeBytes,
                                 String sha256,
                                 String storageProvider,
                                 String storageKey,
                                 SecurityUser user) {
        String sql = """
                INSERT INTO conversation_attachments(
                    attachment_code, conversation_id, direction, original_filename, mime_type,
                    size_bytes, sha256, storage_provider, storage_key, status, metadata,
                    tenant_id, organization_id, workspace_id, project_id, created_by, updated_by
                ) VALUES (?, ?, 'input', ?, ?, ?, ?, ?, ?, 'ready', '{}'::jsonb, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            int index = 1;
            statement.setString(index++, attachmentCode);
            statement.setLong(index++, conversationId);
            statement.setString(index++, originalFilename);
            statement.setString(index++, mimeType);
            statement.setLong(index++, sizeBytes);
            statement.setString(index++, sha256);
            statement.setString(index++, storageProvider);
            statement.setString(index++, storageKey);
            statement.setLong(index++, scopeValue(user.tenantId()));
            statement.setLong(index++, scopeValue(user.organizationId()));
            statement.setLong(index++, scopeValue(user.workspaceId()));
            statement.setLong(index++, scopeValue(user.projectId()));
            statement.setLong(index++, user.userId());
            statement.setLong(index, user.userId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("新增会话附件后未返回主键");
        }
        return key.longValue();
    }

    /** 按附件和会话查询当前用户可访问的记录。 */
    public Optional<ConversationAttachmentRecord> findOwnedById(Long conversationId, Long attachmentId, Long userId) {
        List<ConversationAttachmentRecord> records = jdbcTemplate.query(
                SELECT_COLUMNS + """
                        WHERE attachment.id = ?
                          AND attachment.conversation_id = ?
                          AND conversation.user_id = ?
                          AND attachment.status = 'ready'
                          AND attachment.is_deleted = FALSE
                          AND conversation.is_deleted = FALSE
                        LIMIT 1
                        """, ROW_MAPPER, attachmentId, conversationId, userId);
        return records.stream().findFirst();
    }

    /** 运行时按会话和附件ID读取已绑定的附件元数据。 */
    public Optional<ConversationAttachmentRecord> findReadyRuntimeAttachment(Long conversationId, Long attachmentId) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
                WHERE attachment.id = ? AND attachment.conversation_id = ?
                  AND attachment.message_id IS NOT NULL
                  AND attachment.status = 'ready' AND attachment.is_deleted = FALSE
                  AND conversation.is_deleted = FALSE
                LIMIT 1
                """, ROW_MAPPER, attachmentId, conversationId).stream().findFirst();
    }

    /** 查询消息已绑定的可用附件。 */
    public List<ConversationAttachmentRecord> findReadyByMessageId(Long conversationId, Long messageId) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
                WHERE attachment.conversation_id = ?
                  AND attachment.message_id = ?
                  AND attachment.status = 'ready'
                  AND attachment.is_deleted = FALSE
                  AND conversation.is_deleted = FALSE
                ORDER BY attachment.id
                """, ROW_MAPPER, conversationId, messageId);
    }

    /** 将尚未使用的附件原子预留给异步 Run。 */
    public int reserveForRun(Long conversationId, List<Long> attachmentIds, String runCode, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(attachmentIds.size(), "?"));
        String sql = """
                UPDATE conversation_attachments attachment
                SET run_code = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE attachment.conversation_id = ?
                  AND attachment.id IN (%s)
                  AND attachment.created_by = ?
                  AND attachment.message_id IS NULL
                  AND attachment.run_code IS NULL
                  AND attachment.status = 'ready'
                  AND attachment.is_deleted = FALSE
                """.formatted(placeholders);
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(runCode);
        params.add(userId);
        params.add(conversationId);
        params.addAll(attachmentIds);
        params.add(userId);
        return jdbcTemplate.update(sql, params.toArray());
    }

    /** 将同步消息指定的附件绑定到用户消息。 */
    public int bindToMessage(Long conversationId, List<Long> attachmentIds, Long messageId, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(attachmentIds.size(), "?"));
        String sql = """
                UPDATE conversation_attachments attachment
                SET message_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE attachment.conversation_id = ?
                  AND attachment.id IN (%s)
                  AND attachment.created_by = ?
                  AND attachment.message_id IS NULL
                  AND attachment.run_code IS NULL
                  AND attachment.status = 'ready'
                  AND attachment.is_deleted = FALSE
                """.formatted(placeholders);
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(messageId);
        params.add(userId);
        params.add(conversationId);
        params.addAll(attachmentIds);
        params.add(userId);
        return jdbcTemplate.update(sql, params.toArray());
    }

    /** 将当前 Run 已预留的附件绑定到实际用户消息。 */
    public int bindRunToMessage(Long conversationId, String runCode, Long messageId, Long userId) {
        if (runCode == null || runCode.isBlank()) return 0;
        return jdbcTemplate.update("""
                UPDATE conversation_attachments
                SET message_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ? AND run_code = ? AND created_by = ?
                  AND message_id IS NULL AND status = 'ready' AND is_deleted = FALSE
                """, messageId, userId, conversationId, runCode, userId);
    }

    /** 逻辑删除尚未绑定消息或Run的附件。 */
    public int deleteUnbound(Long conversationId, Long attachmentId, Long userId) {
        return jdbcTemplate.update("""
                UPDATE conversation_attachments
                SET status = 'deleted', is_deleted = TRUE, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND conversation_id = ? AND created_by = ?
                  AND message_id IS NULL AND run_code IS NULL
                  AND status = 'ready' AND is_deleted = FALSE
                """, userId, attachmentId, conversationId, userId);
    }

    private long scopeValue(Long value) {
        return value == null ? 1L : value;
    }
}
