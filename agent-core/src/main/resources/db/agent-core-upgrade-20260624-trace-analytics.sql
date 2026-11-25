CREATE INDEX IF NOT EXISTS idx_task_traces_trace_status ON task_traces (trace_status);
CREATE INDEX IF NOT EXISTS idx_task_traces_task_started_at ON task_traces (task_id, started_at);
CREATE INDEX IF NOT EXISTS idx_approvals_created_at ON approvals (created_at);
CREATE INDEX IF NOT EXISTS idx_approvals_approved_at ON approvals (approved_at);
