CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    organization_code VARCHAR(64) NOT NULL,
    organization_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, organization_code)
);

CREATE TABLE workspaces (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    workspace_code VARCHAR(64) NOT NULL,
    workspace_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, workspace_code)
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, project_code)
);

CREATE TABLE user_scope_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    organization_id BIGINT REFERENCES organizations(id),
    workspace_id BIGINT REFERENCES workspaces(id),
    project_id BIGINT REFERENCES projects(id),
    membership_role VARCHAR(32) NOT NULL DEFAULT 'member',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, tenant_id, organization_id, workspace_id, project_id)
);

INSERT INTO tenants (tenant_code, tenant_name, metadata) VALUES ('DEFAULT', '默认租户', '{"system":true}')
ON CONFLICT (tenant_code) DO NOTHING;
INSERT INTO organizations (tenant_id, organization_code, organization_name, metadata)
SELECT id, 'DEFAULT_ORG', '默认组织', '{"system":true}' FROM tenants WHERE tenant_code = 'DEFAULT'
ON CONFLICT (tenant_id, organization_code) DO NOTHING;
INSERT INTO workspaces (tenant_id, organization_id, workspace_code, workspace_name, metadata)
SELECT t.id, o.id, 'DEFAULT_WORKSPACE', '默认工作区', '{"system":true}' FROM tenants t
JOIN organizations o ON o.tenant_id=t.id AND o.organization_code='DEFAULT_ORG' WHERE t.tenant_code='DEFAULT'
ON CONFLICT (tenant_id, workspace_code) DO NOTHING;
INSERT INTO projects (tenant_id, organization_id, workspace_id, project_code, project_name, metadata)
SELECT t.id,o.id,w.id,'DEFAULT_PROJECT','默认项目','{"system":true}' FROM tenants t
JOIN organizations o ON o.tenant_id=t.id AND o.organization_code='DEFAULT_ORG'
JOIN workspaces w ON w.tenant_id=t.id AND w.workspace_code='DEFAULT_WORKSPACE' WHERE t.tenant_code='DEFAULT'
ON CONFLICT (tenant_id, project_code) DO NOTHING;
INSERT INTO user_scope_memberships (user_id,tenant_id,organization_id,workspace_id,project_id,membership_role,is_default)
SELECT u.id,t.id,o.id,w.id,p.id,'owner',TRUE FROM users u CROSS JOIN tenants t
JOIN organizations o ON o.tenant_id=t.id AND o.organization_code='DEFAULT_ORG'
JOIN workspaces w ON w.tenant_id=t.id AND w.workspace_code='DEFAULT_WORKSPACE'
JOIN projects p ON p.tenant_id=t.id AND p.project_code='DEFAULT_PROJECT' WHERE t.tenant_code='DEFAULT'
ON CONFLICT (user_id,tenant_id,organization_id,workspace_id,project_id) DO NOTHING;

DO $$ DECLARE table_name TEXT; BEGIN
  FOREACH table_name IN ARRAY ARRAY['models','prompts','tools','workflows','agents','knowledge_bases','guardrail_policies'] LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id BIGINT', table_name);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS organization_id BIGINT', table_name);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS workspace_id BIGINT', table_name);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS project_id BIGINT', table_name);
    EXECUTE format('UPDATE %I SET tenant_id=t.id, organization_id=o.id, workspace_id=w.id, project_id=p.id FROM tenants t JOIN organizations o ON o.tenant_id=t.id AND o.organization_code=''DEFAULT_ORG'' JOIN workspaces w ON w.tenant_id=t.id AND w.workspace_code=''DEFAULT_WORKSPACE'' JOIN projects p ON p.tenant_id=t.id AND p.project_code=''DEFAULT_PROJECT'' WHERE t.tenant_code=''DEFAULT'' AND %I.tenant_id IS NULL', table_name, table_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_tenant_project ON %I(tenant_id, project_id)', table_name, table_name);
      EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT COALESCE(NULLIF(NULLIF(current_setting(''app.tenant_id'',true),''''),''0'')::BIGINT,1)', table_name);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN organization_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.organization_id'',true),'''')::BIGINT,1)', table_name);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN workspace_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.workspace_id'',true),'''')::BIGINT,1)', table_name);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN project_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.project_id'',true),'''')::BIGINT,1)', table_name);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', table_name);
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
    EXECUTE format('CREATE POLICY tenant_scope_policy ON %I USING (COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)) WITH CHECK (COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0))', table_name);
  END LOOP;
END $$;

COMMENT ON TABLE tenants IS '租户根边界';
COMMENT ON TABLE organizations IS '租户内组织';
COMMENT ON TABLE workspaces IS '组织内资源工作区';
COMMENT ON TABLE projects IS '工作区内项目级隔离边界';
COMMENT ON TABLE user_scope_memberships IS '用户可切换的租户组织工作区项目成员关系';
