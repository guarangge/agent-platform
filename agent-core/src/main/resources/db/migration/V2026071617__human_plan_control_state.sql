ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS ck_agent_runs_status;
ALTER TABLE agent_runs ADD CONSTRAINT ck_agent_runs_status
CHECK (status IN ('queued','running','completed','waiting_approval','waiting_event','paused','failed','cancelled'));

ALTER TABLE task_execution_checkpoints DROP CONSTRAINT IF EXISTS ck_task_checkpoint_status;
ALTER TABLE task_execution_checkpoints ADD CONSTRAINT ck_task_checkpoint_status
CHECK (checkpoint_status IN ('step_started','step_succeeded','step_failed','waiting_approval','waiting_event','paused'));

COMMENT ON COLUMN tasks.status IS '任务状态：pending/processing/success/failed/waiting_approval/waiting_event/paused';
COMMENT ON COLUMN task_steps.status IS '步骤状态：pending/processing/success/failed/waiting_approval/waiting_event/paused';
COMMENT ON COLUMN agent_runs.status IS '运行状态：queued/running/completed/waiting_approval/waiting_event/paused/failed/cancelled';
