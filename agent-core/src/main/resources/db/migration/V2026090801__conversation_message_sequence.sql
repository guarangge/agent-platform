ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS message_sequence INTEGER NOT NULL DEFAULT 0;

UPDATE conversations conversation
SET message_sequence = existing.max_sequence
FROM (
    SELECT conversation_id, COALESCE(MAX(sequence_no), 0) AS max_sequence
    FROM messages
    WHERE is_deleted = FALSE
    GROUP BY conversation_id
) existing
WHERE conversation.id = existing.conversation_id
  AND conversation.message_sequence < existing.max_sequence;

COMMENT ON COLUMN conversations.message_sequence IS
    '会话内消息序号原子计数器，避免并发 Worker 使用 MAX(sequence_no)+1 产生重复序号';
