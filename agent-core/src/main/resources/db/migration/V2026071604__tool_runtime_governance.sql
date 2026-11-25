CREATE TABLE IF NOT EXISTS tool_instance_runtime_states (
    deployment_id BIGINT PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    deployment_code VARCHAR(64) NOT NULL,
    circuit_state VARCHAR(32) NOT NULL DEFAULT 'closed',
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    total_successes BIGINT NOT NULL DEFAULT 0,
    total_failures BIGINT NOT NULL DEFAULT 0,
    last_success_at TIMESTAMP,
    last_failure_at TIMESTAMP,
    last_error VARCHAR(1000),
    opened_at TIMESTAMP,
    next_probe_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_runtime_states_tool ON tool_instance_runtime_states (tool_id, circuit_state);

CREATE TABLE IF NOT EXISTS tool_runtime_route_audits (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    deployment_id BIGINT,
    tool_code VARCHAR(64) NOT NULL,
    deployment_code VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64),
    error_code VARCHAR(128),
    message VARCHAR(1000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_route_audits_tool ON tool_runtime_route_audits (tool_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_route_audits_trace ON tool_runtime_route_audits (trace_id);

COMMENT ON TABLE tool_instance_runtime_states IS 'Tool实例熔断、恢复探测及运行统计状态';
COMMENT ON TABLE tool_runtime_route_audits IS 'Tool实例路由、故障切换与熔断审计';
ALTER TABLE tool_instance_runtime_states ADD CONSTRAINT ck_tool_runtime_circuit_state
    CHECK (circuit_state IN ('closed', 'open', 'half_open'));
COMMENT ON COLUMN tool_instance_runtime_states.deployment_id IS '注册中心部署实例ID';
COMMENT ON COLUMN tool_instance_runtime_states.tool_id IS '注册中心Tool ID';
COMMENT ON COLUMN tool_instance_runtime_states.deployment_code IS '部署实例编码快照';
COMMENT ON COLUMN tool_instance_runtime_states.circuit_state IS '熔断状态';
COMMENT ON COLUMN tool_instance_runtime_states.consecutive_failures IS '连续失败次数';
COMMENT ON COLUMN tool_instance_runtime_states.total_successes IS '累计成功次数';
COMMENT ON COLUMN tool_instance_runtime_states.total_failures IS '累计失败次数';
COMMENT ON COLUMN tool_instance_runtime_states.next_probe_at IS '下次半开探测时间';
COMMENT ON COLUMN tool_runtime_route_audits.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_runtime_route_audits.deployment_id IS '部署实例ID';
COMMENT ON COLUMN tool_runtime_route_audits.action IS '路由或熔断动作';
COMMENT ON COLUMN tool_runtime_route_audits.result IS '动作结果';
COMMENT ON COLUMN tool_runtime_route_audits.trace_id IS '关联运行链路ID';
COMMENT ON COLUMN tool_runtime_route_audits.error_code IS '标准错误码';
COMMENT ON COLUMN tool_runtime_route_audits.metadata IS '路由扩展元数据';
