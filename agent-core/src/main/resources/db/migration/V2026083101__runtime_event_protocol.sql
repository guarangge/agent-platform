ALTER TABLE agent_run_stream_events
    ADD COLUMN IF NOT EXISTS payload_version SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE agent_run_stream_events
    DROP CONSTRAINT IF EXISTS ck_agent_run_stream_events_type;

ALTER TABLE agent_run_stream_events
    ALTER COLUMN event_type TYPE VARCHAR(64);

ALTER TABLE agent_run_stream_events
    ADD CONSTRAINT ck_agent_run_stream_events_type
        CHECK (event_type = BTRIM(event_type) AND CHAR_LENGTH(event_type) BETWEEN 1 AND 64);

COMMENT ON TABLE agent_run_stream_events IS 'Agent Run 持久化 Runtime Event，支持统一协议、跨实例消费与断线续传';
COMMENT ON COLUMN agent_run_stream_events.event_type IS 'Runtime Event 类型；兼容历史 reset、delta、progress，并支持 message.*、run.* 等版本化事件';
COMMENT ON COLUMN agent_run_stream_events.content IS '事件正文；结构化扩展信息保存在 metadata';
COMMENT ON COLUMN agent_run_stream_events.payload_version IS 'Runtime Event Payload Schema 版本';
