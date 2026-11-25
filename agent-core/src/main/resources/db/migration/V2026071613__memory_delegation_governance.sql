CREATE TABLE IF NOT EXISTS memory_governance_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    organization_id BIGINT,
    workspace_id BIGINT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    conversation_id BIGINT,
    source_task_id BIGINT,
    source_message_id BIGINT,
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    supersedes_memory_id BIGINT,
    superseded_by_memory_id BIGINT,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    revoke_reason VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT ck_memory_governance_type CHECK (memory_type IN ('preference','attention','fact','goal')),
    CONSTRAINT ck_memory_governance_confidence CHECK (confidence >= 0 AND confidence <= 1)
);
CREATE INDEX IF NOT EXISTS idx_memory_governance_active
    ON memory_governance_items (tenant_id, project_id, user_id, agent_id, memory_type, updated_at DESC)
    WHERE revoked_at IS NULL;
ALTER TABLE memory_governance_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE memory_governance_items FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON memory_governance_items;
CREATE POLICY tenant_scope_policy ON memory_governance_items
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));

ALTER TABLE task_delegations ADD COLUMN IF NOT EXISTS delegation_depth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE task_delegations ADD COLUMN IF NOT EXISTS required_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE task_delegations ADD COLUMN IF NOT EXISTS selection_reason VARCHAR(500);
ALTER TABLE task_delegations ADD COLUMN IF NOT EXISTS timeout_at TIMESTAMP;
ALTER TABLE task_delegations ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP;

COMMENT ON TABLE memory_governance_items IS '可追溯、可过期、可撤销和可替代的用户长期记忆项';
COMMENT ON COLUMN memory_governance_items.id IS '记忆项主键';
COMMENT ON COLUMN memory_governance_items.tenant_id IS '租户 ID';
COMMENT ON COLUMN memory_governance_items.organization_id IS '组织 ID';
COMMENT ON COLUMN memory_governance_items.workspace_id IS '工作空间 ID';
COMMENT ON COLUMN memory_governance_items.project_id IS '项目 ID';
COMMENT ON COLUMN memory_governance_items.user_id IS '记忆所属用户 ID';
COMMENT ON COLUMN memory_governance_items.agent_id IS '记忆隔离的 Agent ID';
COMMENT ON COLUMN memory_governance_items.conversation_id IS '记忆来源会话 ID';
COMMENT ON COLUMN memory_governance_items.source_task_id IS '记忆来源任务 ID';
COMMENT ON COLUMN memory_governance_items.source_message_id IS '记忆来源用户消息 ID';
COMMENT ON COLUMN memory_governance_items.memory_type IS 'preference/attention/fact/goal';
COMMENT ON COLUMN memory_governance_items.content IS '当前有效记忆内容';
COMMENT ON COLUMN memory_governance_items.content_hash IS '记忆内容 SHA-256';
COMMENT ON COLUMN memory_governance_items.confidence IS '记忆置信度 0-1';
COMMENT ON COLUMN memory_governance_items.supersedes_memory_id IS '当前记忆替代的旧记忆 ID';
COMMENT ON COLUMN memory_governance_items.superseded_by_memory_id IS '替代当前记忆的新记忆 ID';
COMMENT ON COLUMN memory_governance_items.expires_at IS '记忆自动失效时间，空表示不过期';
COMMENT ON COLUMN memory_governance_items.revoked_at IS '人工撤销或被替代时间';
COMMENT ON COLUMN memory_governance_items.revoke_reason IS '撤销原因';
COMMENT ON COLUMN memory_governance_items.metadata IS '提取器、来源和治理扩展信息';
COMMENT ON COLUMN memory_governance_items.created_at IS '创建时间';
COMMENT ON COLUMN memory_governance_items.created_by IS '创建人';
COMMENT ON COLUMN memory_governance_items.updated_at IS '更新时间';
COMMENT ON COLUMN memory_governance_items.updated_by IS '更新人';
COMMENT ON COLUMN task_delegations.delegation_depth IS '当前委派在父子任务树中的深度';
COMMENT ON COLUMN task_delegations.required_capabilities IS '委派要求的 Agent 能力列表';
COMMENT ON COLUMN task_delegations.selection_reason IS '目标 Agent 选择原因';
COMMENT ON COLUMN task_delegations.timeout_at IS '委派执行超时时间点';
COMMENT ON COLUMN task_delegations.cancellation_requested_at IS '父任务取消向当前委派传播的时间';
