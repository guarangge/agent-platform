CREATE TABLE IF NOT EXISTS tool_side_effect_resolutions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    side_effect_execution_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    resolved_status VARCHAR(32),
    resolution_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    resolved_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_side_effect_resolution_execution FOREIGN KEY(side_effect_execution_id)
        REFERENCES tool_side_effect_executions(id),
    CONSTRAINT ck_side_effect_resolution_action CHECK(action IN ('confirm_success','retry','compensate','confirm_compensated'))
);

CREATE INDEX IF NOT EXISTS idx_side_effect_resolution_task
    ON tool_side_effect_resolutions(tenant_id,project_id,task_id,created_at DESC);
ALTER TABLE tool_side_effect_resolutions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tool_side_effect_resolutions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON tool_side_effect_resolutions
USING (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));

COMMENT ON TABLE tool_side_effect_resolutions IS '副作用不确定状态逐执行人工处置审计';
COMMENT ON COLUMN tool_side_effect_resolutions.side_effect_execution_id IS '被处置的副作用执行记录 ID';
COMMENT ON COLUMN tool_side_effect_resolutions.action IS 'confirm_success/retry/compensate/confirm_compensated';
COMMENT ON COLUMN tool_side_effect_resolutions.previous_status IS '处置前状态';
COMMENT ON COLUMN tool_side_effect_resolutions.resolved_status IS '处置后状态，失败处置时可为空';
COMMENT ON COLUMN tool_side_effect_resolutions.resolution_snapshot IS '人工确认结果或补偿调用结果快照';
COMMENT ON COLUMN tool_side_effect_resolutions.error_message IS '处置失败原因';
