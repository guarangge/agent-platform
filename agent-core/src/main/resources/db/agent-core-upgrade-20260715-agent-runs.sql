ALTER TABLE tasks ADD COLUMN IF NOT EXISTS run_code VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tasks_run_code ON tasks (run_code) WHERE run_code IS NOT NULL;

COMMENT ON COLUMN tasks.run_code IS '关联异步运行编码，用于幂等执行和队列回链';

CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGSERIAL PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    input_text TEXT NOT NULL,
    user_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 2,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP,
    heartbeat_at TIMESTAMP,
    worker_id VARCHAR(160),
    task_id BIGINT,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_runs_code ON agent_runs (run_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_runs_idempotency
    ON agent_runs (conversation_id, user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_runs_claim
    ON agent_runs (status, available_at, lease_until)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_runs_conversation ON agent_runs (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_runs_task_id ON agent_runs (task_id);

COMMENT ON TABLE agent_runs IS 'Agent 异步运行持久队列表';
COMMENT ON COLUMN agent_runs.id IS '主键ID';
COMMENT ON COLUMN agent_runs.run_code IS '运行编码，唯一';
COMMENT ON COLUMN agent_runs.conversation_id IS '所属会话ID';
COMMENT ON COLUMN agent_runs.user_id IS '发起用户ID';
COMMENT ON COLUMN agent_runs.input_text IS '用户输入文本';
COMMENT ON COLUMN agent_runs.user_context IS '入队时固化的用户身份与权限上下文';
COMMENT ON COLUMN agent_runs.idempotency_key IS '客户端幂等键';
COMMENT ON COLUMN agent_runs.status IS '运行状态：queued、running、completed、waiting_approval、failed、cancelled';
COMMENT ON COLUMN agent_runs.attempt_count IS '已领取执行次数';
COMMENT ON COLUMN agent_runs.max_attempts IS '最大领取执行次数';
COMMENT ON COLUMN agent_runs.available_at IS '下次可领取时间';
COMMENT ON COLUMN agent_runs.lease_until IS 'Worker 租约截止时间';
COMMENT ON COLUMN agent_runs.heartbeat_at IS 'Worker 最近心跳时间';
COMMENT ON COLUMN agent_runs.worker_id IS '当前 Worker 实例标识';
COMMENT ON COLUMN agent_runs.task_id IS '关联执行任务ID';
COMMENT ON COLUMN agent_runs.cancel_requested IS '是否已请求取消';
COMMENT ON COLUMN agent_runs.error_message IS '失败原因';
COMMENT ON COLUMN agent_runs.started_at IS '首次开始时间';
COMMENT ON COLUMN agent_runs.finished_at IS '结束时间';
COMMENT ON COLUMN agent_runs.created_at IS '创建时间';
COMMENT ON COLUMN agent_runs.updated_at IS '更新时间';
COMMENT ON COLUMN agent_runs.is_deleted IS '逻辑删除标记';
