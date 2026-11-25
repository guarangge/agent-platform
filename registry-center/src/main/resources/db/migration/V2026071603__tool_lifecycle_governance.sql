CREATE TABLE IF NOT EXISTS tool_catalogs (
    id BIGSERIAL PRIMARY KEY,
    catalog_code VARCHAR(64) NOT NULL,
    catalog_name VARCHAR(128) NOT NULL,
    catalog_type VARCHAR(32) NOT NULL,
    source_url VARCHAR(500),
    transport_type VARCHAR(64),
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    last_discovered_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_catalogs_code ON tool_catalogs (catalog_code) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_tool_catalogs_status ON tool_catalogs (status);

CREATE TABLE IF NOT EXISTS tool_catalog_items (
    id BIGSERIAL PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    remote_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    description VARCHAR(1000),
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    remote_version VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_catalog_items_remote
    ON tool_catalog_items (catalog_id, remote_name) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS tool_versions (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    invoke_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    release_notes VARCHAR(1000),
    compatibility VARCHAR(32) NOT NULL DEFAULT 'compatible',
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_versions_tool_version
    ON tool_versions (tool_id, version) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_tool_versions_status ON tool_versions (tool_id, status);

CREATE TABLE IF NOT EXISTS tool_deployments (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    deployment_code VARCHAR(64) NOT NULL,
    deployment_name VARCHAR(128) NOT NULL,
    environment_code VARCHAR(64) NOT NULL DEFAULT 'default',
    provider_type VARCHAR(64) NOT NULL,
    transport_type VARCHAR(64) NOT NULL,
    endpoint VARCHAR(500),
    auth_type VARCHAR(64) NOT NULL DEFAULT 'none',
    auth_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    invoke_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    traffic_weight INTEGER NOT NULL DEFAULT 100,
    rollout_percent INTEGER NOT NULL DEFAULT 100,
    priority INTEGER NOT NULL DEFAULT 100,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    retry_times INTEGER NOT NULL DEFAULT 0,
    failure_threshold INTEGER NOT NULL DEFAULT 3,
    recovery_seconds INTEGER NOT NULL DEFAULT 60,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    drain_started_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_tool_deployment_weight CHECK (traffic_weight BETWEEN 0 AND 10000),
    CONSTRAINT ck_tool_deployment_rollout CHECK (rollout_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_tool_deployment_failure_threshold CHECK (failure_threshold > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_deployments_code
    ON tool_deployments (deployment_code) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_tool_deployments_route
    ON tool_deployments (tool_id, environment_code, status, priority);

CREATE TABLE IF NOT EXISTS tool_migrations (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    source_version_id BIGINT,
    target_version_id BIGINT NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    rollout_percent INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    validation_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_tool_migrations_tool ON tool_migrations (tool_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tool_lifecycle_audits (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT,
    deployment_id BIGINT,
    action VARCHAR(64) NOT NULL,
    before_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    result VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    operator_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_lifecycle_audits_tool ON tool_lifecycle_audits (tool_id, created_at DESC);

INSERT INTO tool_versions (tool_id, version, input_schema, output_schema, invoke_config, release_notes,
                           compatibility, status, published_at, created_at, updated_at)
SELECT t.id, '1.0.0', t.input_schema, t.output_schema, COALESCE(tb.invoke_config, '{}'::jsonb),
       '由存量 Tool 自动建立的基线版本', 'compatible',
       CASE WHEN t.status = 'enabled' THEN 'published' ELSE 'draft' END,
       CASE WHEN t.status = 'enabled' THEN CURRENT_TIMESTAMP ELSE NULL END,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tools t
LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
WHERE t.is_deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM tool_versions tv WHERE tv.tool_id = t.id AND tv.is_deleted = FALSE);

INSERT INTO tool_deployments (tool_id, version_id, deployment_code, deployment_name, environment_code,
                              provider_type, transport_type, endpoint, auth_type, auth_config, headers,
                              invoke_config, timeout_seconds, retry_times, status, created_at, updated_at)
SELECT DISTINCT ON (t.id) t.id, tv.id,
       LEFT(t.tool_code || '_' || COALESCE(tpi.instance_code, 'default'), 51)
           || '_' || SUBSTRING(md5(t.id::text || ':' || COALESCE(tpi.id::text, '0')) FROM 1 FOR 12),
       COALESCE(tpi.instance_name, t.tool_name || '默认实例'), COALESCE(tpi.environment_code, 'default'),
       COALESCE(tp.provider_type, 'internal'), COALESCE(tp.transport_type, 'in_process'), tpi.endpoint,
       COALESCE(tpi.auth_type, 'none'), COALESCE(tpi.auth_config, '{}'::jsonb),
       COALESCE(tpi.headers, '{}'::jsonb), COALESCE(tb.invoke_config, '{}'::jsonb),
       COALESCE(tpi.timeout_seconds, t.timeout_seconds, 30), COALESCE(tpi.retry_times, 0),
       CASE WHEN t.status = 'enabled' THEN 'enabled' ELSE 'disabled' END, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tools t
JOIN tool_versions tv ON tv.tool_id = t.id AND tv.is_deleted = FALSE
LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
LEFT JOIN tool_provider_instances tpi ON tpi.id = tb.provider_instance_id AND tpi.is_deleted = FALSE
WHERE t.is_deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM tool_deployments td WHERE td.tool_id = t.id AND td.is_deleted = FALSE)
ORDER BY t.id, tb.id NULLS LAST;

COMMENT ON TABLE tool_catalogs IS 'Tool安装与发现目录';
COMMENT ON TABLE tool_catalog_items IS 'Tool目录远程发现条目';
COMMENT ON TABLE tool_versions IS 'Tool不可变版本快照';
COMMENT ON TABLE tool_deployments IS 'Tool版本部署实例与灰度路由配置';
COMMENT ON TABLE tool_migrations IS 'Tool版本迁移任务';
COMMENT ON TABLE tool_lifecycle_audits IS 'Tool生命周期操作审计';

ALTER TABLE tool_catalog_items ADD CONSTRAINT fk_tool_catalog_items_catalog
    FOREIGN KEY (catalog_id) REFERENCES tool_catalogs(id) ON DELETE CASCADE;
ALTER TABLE tool_versions ADD CONSTRAINT fk_tool_versions_tool
    FOREIGN KEY (tool_id) REFERENCES tools(id);
ALTER TABLE tool_deployments ADD CONSTRAINT fk_tool_deployments_tool
    FOREIGN KEY (tool_id) REFERENCES tools(id);
ALTER TABLE tool_deployments ADD CONSTRAINT fk_tool_deployments_version
    FOREIGN KEY (version_id) REFERENCES tool_versions(id);
ALTER TABLE tool_migrations ADD CONSTRAINT fk_tool_migrations_tool
    FOREIGN KEY (tool_id) REFERENCES tools(id);
ALTER TABLE tool_migrations ADD CONSTRAINT fk_tool_migrations_source_version
    FOREIGN KEY (source_version_id) REFERENCES tool_versions(id);
ALTER TABLE tool_migrations ADD CONSTRAINT fk_tool_migrations_target_version
    FOREIGN KEY (target_version_id) REFERENCES tool_versions(id);
ALTER TABLE tool_catalogs ADD CONSTRAINT ck_tool_catalogs_status CHECK (status IN ('enabled', 'disabled'));
ALTER TABLE tool_versions ADD CONSTRAINT ck_tool_versions_status CHECK (status IN ('draft', 'published', 'deprecated', 'retired'));
ALTER TABLE tool_versions ADD CONSTRAINT ck_tool_versions_compatibility CHECK (compatibility IN ('compatible', 'breaking', 'unknown'));
ALTER TABLE tool_deployments ADD CONSTRAINT ck_tool_deployments_status CHECK (status IN ('enabled', 'disabled', 'draining'));
ALTER TABLE tool_migrations ADD CONSTRAINT ck_tool_migrations_status CHECK (status IN ('pending', 'running', 'completed', 'failed'));
ALTER TABLE tool_migrations ADD CONSTRAINT ck_tool_migrations_rollout CHECK (rollout_percent BETWEEN 0 AND 100);

COMMENT ON COLUMN tool_catalogs.catalog_code IS '安装发现目录编码';
COMMENT ON COLUMN tool_catalogs.catalog_type IS '目录类型';
COMMENT ON COLUMN tool_catalogs.source_url IS '远程发现地址';
COMMENT ON COLUMN tool_catalogs.transport_type IS '发现传输协议';
COMMENT ON COLUMN tool_catalogs.config IS '目录连接配置';
COMMENT ON COLUMN tool_catalogs.last_discovered_at IS '最近发现时间';
COMMENT ON COLUMN tool_catalogs.last_error IS '最近发现错误';
COMMENT ON COLUMN tool_catalog_items.catalog_id IS '所属目录ID';
COMMENT ON COLUMN tool_catalog_items.remote_name IS '远程Tool名称';
COMMENT ON COLUMN tool_catalog_items.input_schema IS '远程输入Schema';
COMMENT ON COLUMN tool_catalog_items.remote_version IS '远程版本';
COMMENT ON COLUMN tool_versions.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_versions.version IS '不可变版本号';
COMMENT ON COLUMN tool_versions.invoke_config IS '版本调用配置快照';
COMMENT ON COLUMN tool_versions.compatibility IS '兼容性标记';
COMMENT ON COLUMN tool_versions.status IS '版本状态';
COMMENT ON COLUMN tool_deployments.version_id IS '部署版本ID';
COMMENT ON COLUMN tool_deployments.deployment_code IS '部署实例编码';
COMMENT ON COLUMN tool_deployments.environment_code IS '环境编码';
COMMENT ON COLUMN tool_deployments.auth_config IS 'AES-GCM加密的认证配置';
COMMENT ON COLUMN tool_deployments.headers IS 'AES-GCM加密的请求头';
COMMENT ON COLUMN tool_deployments.traffic_weight IS '加权路由权重';
COMMENT ON COLUMN tool_deployments.rollout_percent IS '灰度命中百分比';
COMMENT ON COLUMN tool_deployments.failure_threshold IS '连续失败熔断阈值';
COMMENT ON COLUMN tool_deployments.recovery_seconds IS '熔断恢复探测间隔秒';
COMMENT ON COLUMN tool_deployments.status IS '实例状态';
COMMENT ON COLUMN tool_deployments.drain_started_at IS '开始排空流量时间';
COMMENT ON COLUMN tool_migrations.strategy IS '版本迁移策略';
COMMENT ON COLUMN tool_migrations.rollout_percent IS '目标版本灰度百分比';
COMMENT ON COLUMN tool_migrations.validation_result IS 'Schema兼容性校验结果';
COMMENT ON COLUMN tool_lifecycle_audits.action IS '生命周期操作';
COMMENT ON COLUMN tool_lifecycle_audits.before_state IS '操作前快照';
COMMENT ON COLUMN tool_lifecycle_audits.after_state IS '操作后快照';
