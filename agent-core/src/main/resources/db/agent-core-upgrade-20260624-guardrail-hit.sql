CREATE TABLE IF NOT EXISTS guardrail_policy_hits (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_step_id BIGINT,
    agent_id BIGINT NOT NULL,
    trace_id VARCHAR(64),
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    policy_version INTEGER,
    decision_type VARCHAR(32) NOT NULL,
    resource_kind VARCHAR(32) NOT NULL,
    resource_id BIGINT,
    resource_code VARCHAR(64),
    resource_name VARCHAR(128),
    hit_detail VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_conversation_id ON guardrail_policy_hits (conversation_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_task_id ON guardrail_policy_hits (task_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_trace_id ON guardrail_policy_hits (trace_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_policy_code ON guardrail_policy_hits (policy_code);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_decision_type ON guardrail_policy_hits (decision_type);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_created_at ON guardrail_policy_hits (created_at);

COMMENT ON TABLE guardrail_policy_hits IS 'Guardrail 策略命中记录表';
COMMENT ON COLUMN guardrail_policy_hits.id IS '主键ID';
COMMENT ON COLUMN guardrail_policy_hits.conversation_id IS '会话ID';
COMMENT ON COLUMN guardrail_policy_hits.task_id IS '任务ID';
COMMENT ON COLUMN guardrail_policy_hits.task_step_id IS '任务步骤ID';
COMMENT ON COLUMN guardrail_policy_hits.agent_id IS 'Agent ID';
COMMENT ON COLUMN guardrail_policy_hits.trace_id IS '追踪ID';
COMMENT ON COLUMN guardrail_policy_hits.policy_code IS '策略编码';
COMMENT ON COLUMN guardrail_policy_hits.policy_name IS '策略名称';
COMMENT ON COLUMN guardrail_policy_hits.policy_version IS '策略版本';
COMMENT ON COLUMN guardrail_policy_hits.decision_type IS '最终决策类型 block/approval';
COMMENT ON COLUMN guardrail_policy_hits.resource_kind IS '命中的资源类型';
COMMENT ON COLUMN guardrail_policy_hits.resource_id IS '命中的资源ID';
COMMENT ON COLUMN guardrail_policy_hits.resource_code IS '命中的资源编码';
COMMENT ON COLUMN guardrail_policy_hits.resource_name IS '命中的资源名称';
COMMENT ON COLUMN guardrail_policy_hits.hit_detail IS '命中说明';
COMMENT ON COLUMN guardrail_policy_hits.metadata IS '命中元数据';
COMMENT ON COLUMN guardrail_policy_hits.created_at IS '创建时间';
COMMENT ON COLUMN guardrail_policy_hits.created_by IS '创建人';
COMMENT ON COLUMN guardrail_policy_hits.updated_at IS '更新时间';
COMMENT ON COLUMN guardrail_policy_hits.updated_by IS '更新人';
COMMENT ON COLUMN guardrail_policy_hits.is_deleted IS '逻辑删除标记';
