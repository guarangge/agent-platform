CREATE TABLE tool_protocol_compatibility_runs (
 id BIGSERIAL PRIMARY KEY,run_code VARCHAR(64) NOT NULL UNIQUE,tool_id BIGINT NOT NULL,tool_code VARCHAR(128) NOT NULL,
 provider_type VARCHAR(64) NOT NULL,transport_type VARCHAR(64) NOT NULL,protocol_version VARCHAR(64),environment_code VARCHAR(64),
 status VARCHAR(32) NOT NULL,configuration_passed BOOLEAN NOT NULL,connectivity_passed BOOLEAN NOT NULL,discovery_passed BOOLEAN NOT NULL,invocation_passed BOOLEAN NOT NULL,
 latency_ms BIGINT NOT NULL DEFAULT 0,failure_stage VARCHAR(32),failure_reason VARCHAR(1000),checks JSONB NOT NULL DEFAULT '[]',output_snapshot JSONB NOT NULL DEFAULT '{}',
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_tool_compat_status CHECK(status IN('compatible','partially_compatible','incompatible'))
);
CREATE INDEX idx_tool_compat_matrix ON tool_protocol_compatibility_runs(provider_type,transport_type,created_at DESC);
CREATE INDEX idx_tool_compat_tool ON tool_protocol_compatibility_runs(tool_id,created_at DESC);
ALTER TABLE tool_protocol_compatibility_runs ENABLE ROW LEVEL SECURITY;ALTER TABLE tool_protocol_compatibility_runs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON tool_protocol_compatibility_runs USING(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));
COMMENT ON TABLE tool_protocol_compatibility_runs IS '外部 Tool 与 MCP 协议兼容性矩阵执行记录';
COMMENT ON COLUMN tool_protocol_compatibility_runs.checks IS '配置、连接、发现、调用各阶段判定';
COMMENT ON COLUMN tool_protocol_compatibility_runs.output_snapshot IS '脱敏后的协议握手与调用摘要';
