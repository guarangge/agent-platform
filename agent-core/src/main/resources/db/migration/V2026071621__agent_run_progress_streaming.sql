ALTER TABLE agent_run_stream_events
    DROP CONSTRAINT IF EXISTS ck_agent_run_stream_events_type;

ALTER TABLE agent_run_stream_events
    ADD CONSTRAINT ck_agent_run_stream_events_type
        CHECK (event_type IN ('reset', 'delta', 'progress'));

COMMENT ON COLUMN agent_run_stream_events.event_type IS '事件类型：reset、delta、progress';
COMMENT ON COLUMN agent_run_stream_events.content IS '模型正文增量或安全执行进度文案，reset 事件为空';
