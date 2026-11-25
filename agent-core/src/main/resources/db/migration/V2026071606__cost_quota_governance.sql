CREATE TABLE resource_prices (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    input_price_per_1000 NUMERIC(18, 8) NOT NULL DEFAULT 0,
    output_price_per_1000 NUMERIC(18, 8) NOT NULL DEFAULT 0,
    unit_price NUMERIC(18, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_prices_scope UNIQUE (tenant_id, project_id, resource_type, resource_code, effective_from),
    CONSTRAINT ck_resource_prices_values CHECK (input_price_per_1000 >= 0 AND output_price_per_1000 >= 0 AND unit_price >= 0),
    CONSTRAINT ck_resource_prices_status CHECK (status IN ('enabled', 'disabled'))
);

CREATE TABLE budget_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(128) NOT NULL,
    policy_name VARCHAR(200) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT NOT NULL,
    period_type VARCHAR(32) NOT NULL DEFAULT 'monthly',
    budget_amount NUMERIC(18, 6),
    llm_token_limit BIGINT,
    tool_call_limit BIGINT,
    sandbox_second_limit BIGINT,
    delegation_call_limit BIGINT,
    warning_percent NUMERIC(5, 2) NOT NULL DEFAULT 80,
    hard_limit BOOLEAN NOT NULL DEFAULT TRUE,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budget_policies_code_scope UNIQUE (tenant_id, project_id, policy_code),
    CONSTRAINT ck_budget_policies_scope CHECK (scope_type IN ('tenant', 'project', 'agent', 'user')),
    CONSTRAINT ck_budget_policies_period CHECK (period_type IN ('daily', 'monthly')),
    CONSTRAINT ck_budget_policies_warning CHECK (warning_percent > 0 AND warning_percent <= 100),
    CONSTRAINT ck_budget_policies_status CHECK (status IN ('enabled', 'disabled'))
);

CREATE TABLE usage_events (
    id BIGSERIAL PRIMARY KEY,
    event_code VARCHAR(64) NOT NULL UNIQUE,
    resource_type VARCHAR(32) NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    provider VARCHAR(128),
    user_id BIGINT,
    agent_id BIGINT,
    conversation_id BIGINT,
    task_id BIGINT,
    input_units BIGINT NOT NULL DEFAULT 0,
    output_units BIGINT NOT NULL DEFAULT 0,
    quantity NUMERIC(18, 4) NOT NULL DEFAULT 1,
    unit VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    pricing_status VARCHAR(32) NOT NULL DEFAULT 'unpriced',
    execution_status VARCHAR(32) NOT NULL DEFAULT 'success',
    duration_ms BIGINT,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usage_events_resource CHECK (resource_type IN ('llm', 'tool', 'mcp', 'sandbox', 'delegation')),
    CONSTRAINT ck_usage_events_values CHECK (input_units >= 0 AND output_units >= 0 AND quantity >= 0 AND amount >= 0)
);

CREATE TABLE budget_alerts (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES budget_policies(id),
    alert_type VARCHAR(32) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    current_value NUMERIC(18, 6) NOT NULL,
    limit_value NUMERIC(18, 6) NOT NULL,
    usage_percent NUMERIC(8, 2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    message VARCHAR(1000) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_budget_alerts_type CHECK (alert_type IN ('warning', 'exceeded')),
    CONSTRAINT ck_budget_alerts_status CHECK (status IN ('open', 'acknowledged', 'resolved'))
);

CREATE INDEX idx_resource_prices_lookup ON resource_prices (tenant_id, project_id, resource_type, resource_code, status, effective_from DESC);
CREATE INDEX idx_budget_policies_lookup ON budget_policies (tenant_id, project_id, scope_type, scope_id, status, priority);
CREATE INDEX idx_usage_events_scope_time ON usage_events (tenant_id, project_id, occurred_at DESC);
CREATE INDEX idx_usage_events_agent_time ON usage_events (agent_id, occurred_at DESC);
CREATE INDEX idx_usage_events_user_time ON usage_events (user_id, occurred_at DESC);
CREATE INDEX idx_usage_events_resource_time ON usage_events (resource_type, resource_code, occurred_at DESC);
CREATE INDEX idx_budget_alerts_open ON budget_alerts (tenant_id, project_id, status, triggered_at DESC);

ALTER TABLE resource_prices ENABLE ROW LEVEL SECURITY;
ALTER TABLE resource_prices FORCE ROW LEVEL SECURITY;
ALTER TABLE budget_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE budget_policies FORCE ROW LEVEL SECURITY;
ALTER TABLE usage_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_events FORCE ROW LEVEL SECURITY;
ALTER TABLE budget_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE budget_alerts FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_scope_policy ON resource_prices USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
CREATE POLICY tenant_scope_policy ON budget_policies USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
CREATE POLICY tenant_scope_policy ON usage_events USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));
CREATE POLICY tenant_scope_policy ON budget_alerts USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0 OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));

COMMENT ON TABLE resource_prices IS '运行时资源价格表，支持模型、Tool、MCP、Sandbox 与委派计价';
COMMENT ON TABLE budget_policies IS '租户、项目、Agent、用户级预算与配额策略';
COMMENT ON TABLE usage_events IS '统一运行时用量与成本流水账';
COMMENT ON TABLE budget_alerts IS '预算阈值命中、超限及处理记录';
COMMENT ON COLUMN usage_events.input_units IS 'LLM 输入 Token 等输入单位数';
COMMENT ON COLUMN usage_events.output_units IS 'LLM 输出 Token 等输出单位数';
COMMENT ON COLUMN usage_events.pricing_status IS '计价状态：priced 或 unpriced';
COMMENT ON COLUMN budget_policies.hard_limit IS '是否在超过限制时阻止资源调用';
COMMENT ON COLUMN budget_policies.warning_percent IS '生成预警的使用率阈值';
