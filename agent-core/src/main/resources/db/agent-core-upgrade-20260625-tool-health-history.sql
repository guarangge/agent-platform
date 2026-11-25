CREATE TABLE IF NOT EXISTS tool_health_checks (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64),
    transport_type VARCHAR(64),
    provider_code VARCHAR(64),
    provider_name VARCHAR(128),
    provider_instance_code VARCHAR(64),
    provider_instance_name VARCHAR(128),
    environment_code VARCHAR(64),
    endpoint VARCHAR(512),
    operation_name VARCHAR(128),
    operation_type VARCHAR(64),
    health_status VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(1000),
    failure_reason VARCHAR(1000),
    status_code INTEGER,
    latency_ms BIGINT,
    output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tool_health_checks_tool_id ON tool_health_checks (tool_id);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_tool_checked_at ON tool_health_checks (tool_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_instance_code ON tool_health_checks (provider_instance_code);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_health_status ON tool_health_checks (health_status);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_checked_at ON tool_health_checks (checked_at);

COMMENT ON TABLE tool_health_checks IS 'Tool健康检查历史表';
COMMENT ON COLUMN tool_health_checks.id IS '主键ID';
COMMENT ON COLUMN tool_health_checks.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_health_checks.tool_code IS 'Tool编码';
COMMENT ON COLUMN tool_health_checks.tool_name IS 'Tool名称';
COMMENT ON COLUMN tool_health_checks.provider_type IS 'Provider类型';
COMMENT ON COLUMN tool_health_checks.transport_type IS '传输协议';
COMMENT ON COLUMN tool_health_checks.provider_code IS 'Provider编码';
COMMENT ON COLUMN tool_health_checks.provider_name IS 'Provider名称';
COMMENT ON COLUMN tool_health_checks.provider_instance_code IS 'Provider实例编码';
COMMENT ON COLUMN tool_health_checks.provider_instance_name IS 'Provider实例名称';
COMMENT ON COLUMN tool_health_checks.environment_code IS '环境编码';
COMMENT ON COLUMN tool_health_checks.endpoint IS '实例地址';
COMMENT ON COLUMN tool_health_checks.operation_name IS '绑定操作名';
COMMENT ON COLUMN tool_health_checks.operation_type IS '绑定操作类型';
COMMENT ON COLUMN tool_health_checks.health_status IS '健康状态 healthy/unhealthy/unknown';
COMMENT ON COLUMN tool_health_checks.success IS '是否成功';
COMMENT ON COLUMN tool_health_checks.message IS '检查结果说明';
COMMENT ON COLUMN tool_health_checks.failure_reason IS '最近失败原因';
COMMENT ON COLUMN tool_health_checks.status_code IS 'Provider返回状态码';
COMMENT ON COLUMN tool_health_checks.latency_ms IS '检查耗时毫秒';
COMMENT ON COLUMN tool_health_checks.output_snapshot IS '检查输出快照';
COMMENT ON COLUMN tool_health_checks.checked_at IS '检查时间';
COMMENT ON COLUMN tool_health_checks.created_at IS '创建时间';
COMMENT ON COLUMN tool_health_checks.created_by IS '创建人';
COMMENT ON COLUMN tool_health_checks.updated_at IS '更新时间';
COMMENT ON COLUMN tool_health_checks.updated_by IS '更新人';
COMMENT ON COLUMN tool_health_checks.is_deleted IS '逻辑删除标记';
