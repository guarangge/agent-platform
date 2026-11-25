CREATE TABLE IF NOT EXISTS interactive_loop_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    organization_id BIGINT,
    workspace_id BIGINT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    sequence_no INTEGER NOT NULL,
    checkpoint_status VARCHAR(32) NOT NULL,
    budget_snapshot JSONB NOT NULL,
    observations_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    pending_action_snapshot JSONB NOT NULL,
    approval_node_id VARCHAR(255) NOT NULL,
    approval_node_name VARCHAR(255),
    tool_call_id VARCHAR(64),
    parameter_hash VARCHAR(64),
    approval_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    trace_id VARCHAR(96),
    restored_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_interactive_loop_checkpoint_sequence UNIQUE (task_id, sequence_no),
    CONSTRAINT ck_interactive_loop_checkpoint_status CHECK (checkpoint_status IN ('waiting_approval','restored'))
);
CREATE INDEX IF NOT EXISTS idx_interactive_loop_checkpoint_latest
    ON interactive_loop_checkpoints (task_id, sequence_no DESC);
ALTER TABLE interactive_loop_checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE interactive_loop_checkpoints FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON interactive_loop_checkpoints;
CREATE POLICY tenant_scope_policy ON interactive_loop_checkpoints
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
COMMENT ON TABLE interactive_loop_checkpoints IS 'Interactive Loop 审批挂起与恢复检查点';
COMMENT ON COLUMN interactive_loop_checkpoints.budget_snapshot IS '预算策略、累计用量和不可逆 Tool 指纹快照';
COMMENT ON COLUMN interactive_loop_checkpoints.observations_snapshot IS '审批前已完成的有界 Observation';
COMMENT ON COLUMN interactive_loop_checkpoints.pending_action_snapshot IS '触发审批且尚未完成的 Tool Action';
COMMENT ON COLUMN interactive_loop_checkpoints.approval_metadata IS '审批策略脱敏元数据与 Tool 调用绑定';
COMMENT ON COLUMN interactive_loop_checkpoints.restored_at IS '待审批 Tool 实际恢复执行时间';
