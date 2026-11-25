ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS agent_id BIGINT;

UPDATE agent_runs ar
SET agent_id = c.agent_id
FROM conversations c
WHERE ar.conversation_id = c.id
  AND ar.agent_id IS NULL;

ALTER TABLE agent_runs
    ALTER COLUMN agent_id SET NOT NULL;

ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 50;

ALTER TABLE agent_runs
    ADD CONSTRAINT ck_agent_runs_priority
        CHECK (priority >= 0 AND priority <= 100);

CREATE INDEX IF NOT EXISTS idx_agent_runs_fair_claim
    ON agent_runs (user_id, agent_id, status, available_at, priority DESC, id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_agent_runs_active_lease
    ON agent_runs (status, lease_until)
    WHERE is_deleted = FALSE AND status = 'running';

COMMENT ON COLUMN agent_runs.agent_id IS '执行 Agent ID，用于按用户和 Agent 维度公平调度';
COMMENT ON COLUMN agent_runs.priority IS '持久化运行优先级，取值0到100，数值越大越优先';
