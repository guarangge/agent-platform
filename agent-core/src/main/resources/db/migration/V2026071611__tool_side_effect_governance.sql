CREATE TABLE IF NOT EXISTS tool_side_effect_executions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    organization_id BIGINT,
    workspace_id BIGINT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    tool_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    side_effect_level VARCHAR(32) NOT NULL,
    compensation_tool_code VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    compensated_at TIMESTAMP,
    CONSTRAINT uk_tool_side_effect_idempotency UNIQUE (tenant_id, project_id, tool_code, idempotency_key),
    CONSTRAINT ck_tool_side_effect_level CHECK (side_effect_level IN ('idempotent','compensatable','irreversible')),
    CONSTRAINT ck_tool_side_effect_status CHECK (status IN ('processing','success','uncertain','compensating','compensated','compensation_failed'))
);
CREATE INDEX IF NOT EXISTS idx_tool_side_effect_task ON tool_side_effect_executions (tenant_id, project_id, task_id, id DESC);
ALTER TABLE tool_side_effect_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tool_side_effect_executions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON tool_side_effect_executions;
CREATE POLICY tenant_scope_policy ON tool_side_effect_executions
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
COMMENT ON TABLE tool_side_effect_executions IS 'Tool 副作用调用幂等、结果复用与补偿审计';
COMMENT ON COLUMN tool_side_effect_executions.id IS '副作用执行记录主键';
COMMENT ON COLUMN tool_side_effect_executions.tenant_id IS '租户 ID';
COMMENT ON COLUMN tool_side_effect_executions.organization_id IS '组织 ID';
COMMENT ON COLUMN tool_side_effect_executions.workspace_id IS '工作空间 ID';
COMMENT ON COLUMN tool_side_effect_executions.project_id IS '项目 ID';
COMMENT ON COLUMN tool_side_effect_executions.task_id IS '触发副作用的运行时任务 ID';
COMMENT ON COLUMN tool_side_effect_executions.tool_code IS '执行副作用的 Tool Code';
COMMENT ON COLUMN tool_side_effect_executions.idempotency_key IS '调用方或平台生成的稳定幂等键';
COMMENT ON COLUMN tool_side_effect_executions.request_hash IS '归一化输入和参数的 SHA-256，用于阻止幂等键误复用';
COMMENT ON COLUMN tool_side_effect_executions.side_effect_level IS '副作用等级：idempotent/compensatable/irreversible';
COMMENT ON COLUMN tool_side_effect_executions.compensation_tool_code IS '可补偿副作用对应的补偿 Tool Code';
COMMENT ON COLUMN tool_side_effect_executions.status IS 'processing/success/uncertain/compensating/compensated/compensation_failed';
COMMENT ON COLUMN tool_side_effect_executions.result_snapshot IS '成功执行结果快照，用于重复请求直接复用';
COMMENT ON COLUMN tool_side_effect_executions.error_message IS '不确定状态或补偿失败的错误信息';
COMMENT ON COLUMN tool_side_effect_executions.created_at IS '创建时间';
COMMENT ON COLUMN tool_side_effect_executions.updated_at IS '更新时间';
COMMENT ON COLUMN tool_side_effect_executions.completed_at IS '原 Tool 成功完成时间';
COMMENT ON COLUMN tool_side_effect_executions.compensated_at IS '补偿成功时间';
