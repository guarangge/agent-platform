CREATE INDEX IF NOT EXISTS idx_agent_run_stream_events_policy_audit
    ON agent_run_stream_events (created_at DESC, run_code, id DESC)
    WHERE event_type = 'policy.decided';

COMMENT ON INDEX idx_agent_run_stream_events_policy_audit IS
    'Capability Policy 全量决策运营查询部分索引，仅覆盖 policy.decided 事件';
