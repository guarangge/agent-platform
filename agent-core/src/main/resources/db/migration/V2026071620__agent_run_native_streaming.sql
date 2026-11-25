ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS stream_event_sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS agent_run_stream_events (
    id BIGSERIAL PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_stream_events_sequence UNIQUE (run_code, sequence_no),
    CONSTRAINT fk_agent_run_stream_events_run_code FOREIGN KEY (run_code)
        REFERENCES agent_runs (run_code) ON DELETE CASCADE,
    CONSTRAINT ck_agent_run_stream_events_type CHECK (event_type IN ('reset', 'delta'))
);

CREATE INDEX IF NOT EXISTS idx_agent_run_stream_events_cursor
    ON agent_run_stream_events (run_code, sequence_no);

COMMENT ON COLUMN agent_runs.stream_event_sequence IS '原生流事件最新序号，用于并发安全分配 Run 内游标';
COMMENT ON TABLE agent_run_stream_events IS 'Agent Run 模型原生流事件表，支持跨实例消费与断线续传';
COMMENT ON COLUMN agent_run_stream_events.id IS '流事件主键';
COMMENT ON COLUMN agent_run_stream_events.run_code IS '关联 Agent Run 编码';
COMMENT ON COLUMN agent_run_stream_events.sequence_no IS 'Run 内单调递增事件序号';
COMMENT ON COLUMN agent_run_stream_events.event_type IS '事件类型：reset、delta';
COMMENT ON COLUMN agent_run_stream_events.content IS '模型正文增量，reset 事件为空';
COMMENT ON COLUMN agent_run_stream_events.metadata IS '流事件扩展元数据';
COMMENT ON COLUMN agent_run_stream_events.created_at IS '事件创建时间';
