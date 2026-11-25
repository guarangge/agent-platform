DO $$ DECLARE table_name TEXT; BEGIN
  FOREACH table_name IN ARRAY ARRAY['conversations','tasks','agent_runs','user_memories','conversation_memories','sandbox_profiles','sandbox_executions','knowledge_retrieval_logs','eval_suites','eval_runs'] LOOP
    IF to_regclass(table_name) IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1', table_name);
      EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS organization_id BIGINT NOT NULL DEFAULT 1', table_name);
      EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1', table_name);
      EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS project_id BIGINT NOT NULL DEFAULT 1', table_name);
      EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_tenant_project ON %I(tenant_id, project_id)', table_name, table_name);
      EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT COALESCE(NULLIF(NULLIF(current_setting(''app.tenant_id'',true),''''),''0'')::BIGINT,1)', table_name);
      EXECUTE format('ALTER TABLE %I ALTER COLUMN organization_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.organization_id'',true),'''')::BIGINT,1)', table_name);
      EXECUTE format('ALTER TABLE %I ALTER COLUMN workspace_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.workspace_id'',true),'''')::BIGINT,1)', table_name);
      EXECUTE format('ALTER TABLE %I ALTER COLUMN project_id SET DEFAULT COALESCE(NULLIF(current_setting(''app.project_id'',true),'''')::BIGINT,1)', table_name);
      EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
      EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
      EXECUTE format('CREATE POLICY tenant_scope_policy ON %I USING (COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)) WITH CHECK (COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0))', table_name);
    END IF;
  END LOOP;
END $$;

COMMENT ON COLUMN conversations.tenant_id IS '会话所属租户ID，来自JWT当前作用域';
COMMENT ON COLUMN conversations.organization_id IS '会话所属组织ID';
COMMENT ON COLUMN conversations.workspace_id IS '会话所属工作区ID';
COMMENT ON COLUMN conversations.project_id IS '会话所属项目ID';
