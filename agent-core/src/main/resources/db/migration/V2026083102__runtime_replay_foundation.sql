CREATE TABLE IF NOT EXISTS runtime_replays (
    id BIGSERIAL PRIMARY KEY,
    replay_code VARCHAR(64) NOT NULL,
    replay_trace_id VARCHAR(64) NOT NULL,
    replay_mode VARCHAR(32) NOT NULL,
    source_run_code VARCHAR(64),
    source_task_id BIGINT NOT NULL,
    source_task_trace_id BIGINT,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    manifest JSONB NOT NULL,
    input_snapshot JSONB NOT NULL,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT uk_runtime_replays_code UNIQUE (replay_code),
    CONSTRAINT uk_runtime_replays_trace UNIQUE (replay_trace_id),
    CONSTRAINT fk_runtime_replays_task FOREIGN KEY (source_task_id) REFERENCES tasks (id),
    CONSTRAINT fk_runtime_replays_task_trace FOREIGN KEY (source_task_trace_id) REFERENCES task_traces (id),
    CONSTRAINT ck_runtime_replays_mode CHECK (replay_mode IN ('prompt_replay', 'simulation_replay', 'execution_replay')),
    CONSTRAINT ck_runtime_replays_status CHECK (status IN ('pending', 'running', 'succeeded', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_runtime_replays_user_created
    ON runtime_replays (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_runtime_replays_source_task
    ON runtime_replays (source_task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_runtime_replays_source_run
    ON runtime_replays (source_run_code, created_at DESC)
    WHERE source_run_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runtime_replays_mode_status
    ON runtime_replays (replay_mode, status, created_at DESC);

COMMENT ON TABLE runtime_replays IS '独立 Runtime Replay 审计实例；Manifest 和 Input 创建后不可修改';
COMMENT ON COLUMN runtime_replays.replay_code IS 'Replay 稳定编码';
COMMENT ON COLUMN runtime_replays.replay_trace_id IS 'Replay 独立 Trace ID，不复用原执行 Trace';
COMMENT ON COLUMN runtime_replays.replay_mode IS 'prompt_replay/simulation_replay/execution_replay';
COMMENT ON COLUMN runtime_replays.source_run_code IS '原 Agent Run 编码，可为空';
COMMENT ON COLUMN runtime_replays.manifest IS 'Replay 使用的模型、Prompt、Tool Schema 可用性、Policy 和执行模式摘要';
COMMENT ON COLUMN runtime_replays.input_snapshot IS '按来源快照冻结的不可变 Replay Input';
COMMENT ON COLUMN runtime_replays.result_snapshot IS 'Replay 独立输出、指标和 Diff，不回写原任务';
