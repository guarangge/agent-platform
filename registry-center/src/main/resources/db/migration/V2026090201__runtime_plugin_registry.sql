CREATE TABLE runtime_plugins (
    id BIGSERIAL PRIMARY KEY,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_runtime_plugins_status CHECK (status IN ('enabled', 'disabled'))
);

CREATE UNIQUE INDEX uk_runtime_plugins_scope_code
    ON runtime_plugins (tenant_id, project_id, plugin_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_runtime_plugins_scope_status
    ON runtime_plugins (tenant_id, project_id, status) WHERE is_deleted = FALSE;

CREATE TABLE runtime_plugin_versions (
    id BIGSERIAL PRIMARY KEY,
    plugin_id BIGINT NOT NULL REFERENCES runtime_plugins(id),
    version VARCHAR(64) NOT NULL,
    platform_api_version VARCHAR(16) NOT NULL,
    entrypoint VARCHAR(255) NOT NULL,
    manifest JSONB NOT NULL,
    artifact_uri VARCHAR(500) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    release_notes VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    published_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_runtime_plugin_versions_status CHECK (status IN ('draft', 'published', 'deprecated')),
    CONSTRAINT ck_runtime_plugin_versions_sha256 CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uk_runtime_plugin_versions_id_plugin UNIQUE (id, plugin_id)
);

CREATE UNIQUE INDEX uk_runtime_plugin_versions_plugin_version
    ON runtime_plugin_versions (plugin_id, version) WHERE is_deleted = FALSE;
CREATE INDEX idx_runtime_plugin_versions_status
    ON runtime_plugin_versions (plugin_id, status) WHERE is_deleted = FALSE;

CREATE TABLE runtime_plugin_installations (
    id BIGSERIAL PRIMARY KEY,
    plugin_id BIGINT NOT NULL REFERENCES runtime_plugins(id),
    version_id BIGINT NOT NULL,
    environment_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'disabled',
    load_status VARCHAR(32) NOT NULL DEFAULT 'pending_restart',
    last_error VARCHAR(1000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMP,
    disabled_at TIMESTAMP,
    uninstalled_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_runtime_plugin_installations_status
        CHECK (status IN ('enabled', 'disabled', 'uninstalled')),
    CONSTRAINT ck_runtime_plugin_installations_load_status
        CHECK (load_status IN ('pending_restart', 'loaded', 'failed', 'not_loaded')),
    CONSTRAINT fk_runtime_plugin_installations_version
        FOREIGN KEY (version_id, plugin_id) REFERENCES runtime_plugin_versions(id, plugin_id),
    CONSTRAINT uk_runtime_plugin_installations_id_plugin UNIQUE (id, plugin_id)
);

CREATE UNIQUE INDEX uk_runtime_plugin_installations_environment
    ON runtime_plugin_installations (plugin_id, environment_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_runtime_plugin_installations_route
    ON runtime_plugin_installations (environment_code, status, load_status) WHERE is_deleted = FALSE;

CREATE TABLE agent_runtime_plugin_rel (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id),
    plugin_id BIGINT NOT NULL REFERENCES runtime_plugins(id),
    installation_id BIGINT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_agent_runtime_plugin_rel_installation
        FOREIGN KEY (installation_id, plugin_id) REFERENCES runtime_plugin_installations(id, plugin_id)
);

CREATE UNIQUE INDEX uk_agent_runtime_plugin_rel_agent_plugin
    ON agent_runtime_plugin_rel (agent_id, plugin_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_agent_runtime_plugin_rel_installation
    ON agent_runtime_plugin_rel (installation_id) WHERE is_deleted = FALSE;

CREATE TABLE runtime_plugin_lifecycle_audits (
    id BIGSERIAL PRIMARY KEY,
    plugin_id BIGINT NOT NULL REFERENCES runtime_plugins(id),
    version_id BIGINT REFERENCES runtime_plugin_versions(id),
    installation_id BIGINT REFERENCES runtime_plugin_installations(id),
    agent_id BIGINT REFERENCES agents(id),
    action VARCHAR(64) NOT NULL,
    before_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    result VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    operator_id BIGINT,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_runtime_plugin_lifecycle_audits_result CHECK (result IN ('success', 'failed'))
);

CREATE INDEX idx_runtime_plugin_lifecycle_audits_plugin
    ON runtime_plugin_lifecycle_audits (plugin_id, created_at DESC);
CREATE INDEX idx_runtime_plugin_lifecycle_audits_agent
    ON runtime_plugin_lifecycle_audits (agent_id, created_at DESC) WHERE agent_id IS NOT NULL;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'runtime_plugins',
        'runtime_plugin_versions',
        'runtime_plugin_installations',
        'agent_runtime_plugin_rel',
        'runtime_plugin_lifecycle_audits'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_scope_policy ON %I '
            || 'USING (COALESCE(NULLIF(current_setting(''app.tenant_id'', true), '''')::BIGINT, 0) = 0 '
            || 'OR tenant_id = COALESCE(NULLIF(current_setting(''app.tenant_id'', true), '''')::BIGINT, 0)) '
            || 'WITH CHECK (COALESCE(NULLIF(current_setting(''app.tenant_id'', true), '''')::BIGINT, 0) = 0 '
            || 'OR tenant_id = COALESCE(NULLIF(current_setting(''app.tenant_id'', true), '''')::BIGINT, 0))',
            table_name
        );
    END LOOP;
END $$;

COMMENT ON TABLE runtime_plugins IS 'Runtime Plugin 项目作用域定义';
COMMENT ON COLUMN runtime_plugins.plugin_code IS 'Manifest 稳定插件编码';
COMMENT ON COLUMN runtime_plugins.plugin_name IS '插件展示名称';
COMMENT ON COLUMN runtime_plugins.description IS '插件用途说明';
COMMENT ON COLUMN runtime_plugins.status IS '定义状态：enabled/disabled';
COMMENT ON COLUMN runtime_plugins.metadata IS '非敏感扩展元数据';

COMMENT ON TABLE runtime_plugin_versions IS 'Runtime Plugin 不可变 Manifest 与制品版本';
COMMENT ON COLUMN runtime_plugin_versions.plugin_id IS '所属插件定义ID';
COMMENT ON COLUMN runtime_plugin_versions.version IS '严格 SemVer 版本';
COMMENT ON COLUMN runtime_plugin_versions.platform_api_version IS '目标平台 API 主版本';
COMMENT ON COLUMN runtime_plugin_versions.entrypoint IS 'AgentRuntimePlugin SPI Provider 全限定类名';
COMMENT ON COLUMN runtime_plugin_versions.manifest IS '不可变完整 Manifest 快照';
COMMENT ON COLUMN runtime_plugin_versions.artifact_uri IS '插件 Jar 制品地址';
COMMENT ON COLUMN runtime_plugin_versions.artifact_sha256 IS '插件 Jar SHA-256 完整性摘要';
COMMENT ON COLUMN runtime_plugin_versions.release_notes IS '版本发布说明';
COMMENT ON COLUMN runtime_plugin_versions.status IS '版本状态：draft/published/deprecated';
COMMENT ON COLUMN runtime_plugin_versions.published_at IS '首次发布时间';

COMMENT ON TABLE runtime_plugin_installations IS 'Runtime Plugin 环境安装与加载期望状态';
COMMENT ON COLUMN runtime_plugin_installations.plugin_id IS '所属插件定义ID';
COMMENT ON COLUMN runtime_plugin_installations.version_id IS '当前环境安装版本ID';
COMMENT ON COLUMN runtime_plugin_installations.environment_code IS '安装目标环境编码';
COMMENT ON COLUMN runtime_plugin_installations.status IS '期望状态：enabled/disabled/uninstalled';
COMMENT ON COLUMN runtime_plugin_installations.load_status IS '实际加载状态：pending_restart/loaded/failed/not_loaded';
COMMENT ON COLUMN runtime_plugin_installations.last_error IS '最近加载失败原因';
COMMENT ON COLUMN runtime_plugin_installations.metadata IS '非敏感安装元数据';
COMMENT ON COLUMN runtime_plugin_installations.installed_at IS '最近安装或替换版本时间';
COMMENT ON COLUMN runtime_plugin_installations.enabled_at IS '最近启用时间';
COMMENT ON COLUMN runtime_plugin_installations.disabled_at IS '最近停用时间';
COMMENT ON COLUMN runtime_plugin_installations.uninstalled_at IS '最近卸载时间';

COMMENT ON TABLE agent_runtime_plugin_rel IS 'Agent 与 Runtime Plugin 环境安装绑定';
COMMENT ON COLUMN agent_runtime_plugin_rel.agent_id IS 'Agent ID';
COMMENT ON COLUMN agent_runtime_plugin_rel.plugin_id IS 'Runtime Plugin ID';
COMMENT ON COLUMN agent_runtime_plugin_rel.installation_id IS '指定环境安装ID';
COMMENT ON COLUMN agent_runtime_plugin_rel.required IS '插件不可用时是否阻断 Agent 运行';

COMMENT ON TABLE runtime_plugin_lifecycle_audits IS 'Runtime Plugin 注册、版本、安装和 Agent 绑定审计';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.plugin_id IS 'Runtime Plugin ID';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.version_id IS '关联版本ID';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.installation_id IS '关联安装ID';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.agent_id IS '关联 Agent ID';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.action IS '生命周期动作';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.before_state IS '操作前安全快照';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.after_state IS '操作后安全快照';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.result IS '操作结果：success/failed';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.message IS '操作摘要';
COMMENT ON COLUMN runtime_plugin_lifecycle_audits.operator_id IS '操作人ID';
