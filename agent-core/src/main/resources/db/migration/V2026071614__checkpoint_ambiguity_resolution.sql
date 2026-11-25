ALTER TABLE task_execution_checkpoints
    ADD COLUMN IF NOT EXISTS recovery_disposition VARCHAR(32) NOT NULL DEFAULT 'safe_resume',
    ADD COLUMN IF NOT EXISTS ambiguity_reason TEXT,
    ADD COLUMN IF NOT EXISTS ambiguity_resolved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ambiguity_resolved_by BIGINT;

COMMENT ON COLUMN task_execution_checkpoints.recovery_disposition IS '恢复处置：safe_resume/manual_review/retry_authorized/confirmed_success/confirmed_compensated';
COMMENT ON COLUMN task_execution_checkpoints.ambiguity_reason IS '副作用执行结果不确定时的阻断原因';
COMMENT ON COLUMN task_execution_checkpoints.ambiguity_resolved_at IS '人工解决不确定状态的时间';
COMMENT ON COLUMN task_execution_checkpoints.ambiguity_resolved_by IS '人工解决不确定状态的用户 ID';

ALTER TABLE tool_side_effect_executions DROP CONSTRAINT IF EXISTS ck_tool_side_effect_status;
ALTER TABLE tool_side_effect_executions ADD CONSTRAINT ck_tool_side_effect_status CHECK (
    status IN ('processing','success','uncertain','retry_authorized','compensating','compensated','compensation_failed')
);
