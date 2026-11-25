ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS agent_id BIGINT;

UPDATE user_memories
SET agent_id = 0
WHERE agent_id IS NULL;

ALTER TABLE user_memories
    ALTER COLUMN agent_id SET NOT NULL;

DROP INDEX IF EXISTS uk_user_memories_user_deleted;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memories_user_agent_deleted
    ON user_memories (user_id, agent_id, is_deleted);
