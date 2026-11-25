ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS ck_agent_runs_status;
ALTER TABLE agent_runs ADD CONSTRAINT ck_agent_runs_status
CHECK (status IN ('queued','running','completed','waiting_approval','waiting_event','failed','cancelled'));
ALTER TABLE task_execution_checkpoints DROP CONSTRAINT IF EXISTS ck_task_checkpoint_status;
ALTER TABLE task_execution_checkpoints ADD CONSTRAINT ck_task_checkpoint_status
CHECK (checkpoint_status IN ('step_started','step_succeeded','step_failed','waiting_approval','waiting_event'));

CREATE TABLE IF NOT EXISTS task_wait_subscriptions (
    id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL, step_no INTEGER NOT NULL, wait_type VARCHAR(24) NOT NULL,
    event_key VARCHAR(160) NOT NULL, wake_at TIMESTAMP, status VARCHAR(24) NOT NULL DEFAULT 'waiting',
    event_payload JSONB NOT NULL DEFAULT '{}'::jsonb, created_by BIGINT, signaled_by BIGINT,
    signaled_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_wait_step UNIQUE(task_id,step_no), CONSTRAINT uk_task_wait_event UNIQUE(event_key),
    CONSTRAINT ck_task_wait_type CHECK(wait_type IN ('timer','event','webhook')),
    CONSTRAINT ck_task_wait_status CHECK(status IN ('waiting','signaled','cancelled'))
);
CREATE INDEX IF NOT EXISTS idx_task_wait_due ON task_wait_subscriptions(status,wake_at) WHERE status='waiting';
ALTER TABLE task_wait_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_wait_subscriptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_scope_policy ON task_wait_subscriptions;
CREATE POLICY tenant_scope_policy ON task_wait_subscriptions
USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT,0))
WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT,0));
COMMENT ON TABLE task_wait_subscriptions IS '长任务 timer/event/webhook 持久化等待订阅';
COMMENT ON COLUMN task_wait_subscriptions.event_key IS '外部事件或 Webhook 唤醒键';
COMMENT ON COLUMN task_wait_subscriptions.event_payload IS '唤醒事件结构化载荷';
