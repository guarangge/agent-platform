CREATE TABLE IF NOT EXISTS task_execution_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    organization_id BIGINT,
    workspace_id BIGINT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_step_id BIGINT,
    sequence_no INTEGER NOT NULL,
    checkpoint_status VARCHAR(32) NOT NULL,
    current_step_no INTEGER,
    next_step_no INTEGER NOT NULL,
    plan_snapshot JSONB NOT NULL,
    decision_snapshot JSONB NOT NULL,
    step_results_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    trace_id VARCHAR(96),
    restored_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_checkpoint_sequence UNIQUE (task_id, sequence_no),
    CONSTRAINT ck_task_checkpoint_status CHECK (checkpoint_status IN ('step_started','step_succeeded','step_failed','waiting_approval'))
);
CREATE INDEX IF NOT EXISTS idx_task_checkpoint_latest ON task_execution_checkpoints (task_id, sequence_no DESC);
ALTER TABLE task_execution_checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_execution_checkpoints FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON task_execution_checkpoints;
CREATE POLICY tenant_scope_policy ON task_execution_checkpoints
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
COMMENT ON TABLE task_execution_checkpoints IS 'Agent Plan Step 持久化执行检查点，用于进程中断后自动续跑';
COMMENT ON COLUMN task_execution_checkpoints.id IS '检查点主键';
COMMENT ON COLUMN task_execution_checkpoints.tenant_id IS '租户 ID';
COMMENT ON COLUMN task_execution_checkpoints.organization_id IS '组织 ID';
COMMENT ON COLUMN task_execution_checkpoints.workspace_id IS '工作空间 ID';
COMMENT ON COLUMN task_execution_checkpoints.project_id IS '项目 ID';
COMMENT ON COLUMN task_execution_checkpoints.task_id IS '运行时任务 ID';
COMMENT ON COLUMN task_execution_checkpoints.task_step_id IS '检查点对应的任务步骤 ID';
COMMENT ON COLUMN task_execution_checkpoints.sequence_no IS '任务内单调递增的检查点序号';
COMMENT ON COLUMN task_execution_checkpoints.checkpoint_status IS 'step_started/step_succeeded/step_failed/waiting_approval';
COMMENT ON COLUMN task_execution_checkpoints.current_step_no IS '当前计划步骤序号';
COMMENT ON COLUMN task_execution_checkpoints.next_step_no IS '恢复时应执行的计划步骤序号';
COMMENT ON COLUMN task_execution_checkpoints.plan_snapshot IS '不可变执行计划快照';
COMMENT ON COLUMN task_execution_checkpoints.decision_snapshot IS '执行决策快照';
COMMENT ON COLUMN task_execution_checkpoints.step_results_snapshot IS '检查点前已完成步骤结果快照';
COMMENT ON COLUMN task_execution_checkpoints.trace_id IS '原任务 Trace ID';
COMMENT ON COLUMN task_execution_checkpoints.restored_at IS '最近一次从该检查点恢复的时间';
COMMENT ON COLUMN task_execution_checkpoints.created_at IS '创建时间';
