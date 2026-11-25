CREATE TABLE IF NOT EXISTS task_plan_controls (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_step_no INTEGER,
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    comment TEXT,
    requested_by BIGINT,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_plan_control_action CHECK (action IN ('pause','resume','skip_step','single_step')),
    CONSTRAINT ck_task_plan_control_status CHECK (status IN ('pending','consumed','cancelled'))
);
CREATE INDEX IF NOT EXISTS idx_task_plan_control_pending ON task_plan_controls(task_id,status,id DESC);
ALTER TABLE task_plan_controls ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_plan_controls FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON task_plan_controls;
CREATE POLICY tenant_scope_policy ON task_plan_controls
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
COMMENT ON TABLE task_plan_controls IS '人工计划控制请求及步骤边界消费审计';
COMMENT ON COLUMN task_plan_controls.action IS 'pause/resume/skip_step/single_step';
COMMENT ON COLUMN task_plan_controls.target_step_no IS '跳过或单步执行的目标步骤序号';
COMMENT ON COLUMN task_plan_controls.status IS 'pending/consumed/cancelled';
