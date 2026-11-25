CREATE TABLE prompt_versions (
 id BIGSERIAL PRIMARY KEY,prompt_id BIGINT NOT NULL REFERENCES prompts(id),version VARCHAR(64) NOT NULL,
 content TEXT NOT NULL,content_hash VARCHAR(64) NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'published',metadata JSONB NOT NULL DEFAULT '{}',
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),
 organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT uk_prompt_versions UNIQUE(prompt_id,version)
);
INSERT INTO prompt_versions(prompt_id,version,content,content_hash,status,tenant_id,organization_id,workspace_id,project_id,created_by)
SELECT id,version,content,md5(content),'published',tenant_id,organization_id,workspace_id,project_id,created_by FROM prompts ON CONFLICT DO NOTHING;

CREATE TABLE prompt_experiments (
 id BIGSERIAL PRIMARY KEY,experiment_code VARCHAR(128) NOT NULL,experiment_name VARCHAR(200) NOT NULL,
 base_prompt_id BIGINT NOT NULL REFERENCES prompts(id),agent_id BIGINT,stage VARCHAR(32) NOT NULL,
 allocation_key VARCHAR(32) NOT NULL DEFAULT 'conversation',rollout_percent INTEGER NOT NULL DEFAULT 100,
 status VARCHAR(32) NOT NULL DEFAULT 'draft',winner_prompt_id BIGINT REFERENCES prompts(id),started_at TIMESTAMP,ended_at TIMESTAMP,
 metadata JSONB NOT NULL DEFAULT '{}',tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),
 organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 created_by BIGINT,updated_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_prompt_experiments_scope UNIQUE(tenant_id,project_id,experiment_code),CONSTRAINT ck_prompt_experiments_stage CHECK(stage IN('system','decision','planner')),
 CONSTRAINT ck_prompt_experiments_allocation CHECK(allocation_key IN('user','conversation')),CONSTRAINT ck_prompt_experiments_rollout CHECK(rollout_percent BETWEEN 1 AND 100),CONSTRAINT ck_prompt_experiments_status CHECK(status IN('draft','running','paused','completed','cancelled'))
);
CREATE TABLE prompt_experiment_variants (
 id BIGSERIAL PRIMARY KEY,experiment_id BIGINT NOT NULL REFERENCES prompt_experiments(id),variant_code VARCHAR(64) NOT NULL,
 variant_name VARCHAR(128) NOT NULL,prompt_id BIGINT NOT NULL REFERENCES prompts(id),weight INTEGER NOT NULL,is_control BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(32) NOT NULL DEFAULT 'enabled',metadata JSONB NOT NULL DEFAULT '{}',created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),
 organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 CONSTRAINT uk_prompt_experiment_variants UNIQUE(experiment_id,variant_code),CONSTRAINT ck_prompt_variant_weight CHECK(weight>0),CONSTRAINT ck_prompt_variant_status CHECK(status IN('enabled','disabled'))
);

CREATE TABLE tool_input_contracts (
 id BIGSERIAL PRIMARY KEY,tool_id BIGINT NOT NULL REFERENCES tools(id),contract_version VARCHAR(64) NOT NULL,input_schema JSONB NOT NULL,
 max_payload_chars INTEGER NOT NULL DEFAULT 20000,sensitive_paths JSONB NOT NULL DEFAULT '[]',compatibility VARCHAR(32) NOT NULL DEFAULT 'compatible',
 status VARCHAR(32) NOT NULL DEFAULT 'draft',release_notes TEXT,created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),
 organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 CONSTRAINT uk_tool_input_contracts UNIQUE(tool_id,contract_version),CONSTRAINT ck_tool_contract_size CHECK(max_payload_chars>0),
 CONSTRAINT ck_tool_contract_compatibility CHECK(compatibility IN('compatible','breaking','unknown')),CONSTRAINT ck_tool_contract_status CHECK(status IN('draft','published','deprecated','retired'))
);
CREATE TABLE tool_contract_test_cases (
 id BIGSERIAL PRIMARY KEY,contract_id BIGINT NOT NULL REFERENCES tool_input_contracts(id),case_name VARCHAR(200) NOT NULL,
 input_payload JSONB NOT NULL,expected_valid BOOLEAN NOT NULL,error_contains VARCHAR(500),status VARCHAR(32) NOT NULL DEFAULT 'enabled',created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),
 organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1)
);
INSERT INTO tool_input_contracts(tool_id,contract_version,input_schema,max_payload_chars,status,tenant_id,organization_id,workspace_id,project_id,created_by)
SELECT id,'1.0.0',input_schema,COALESCE(NULLIF((metadata->'guardrails'->>'maxInputLength')::INTEGER,0),20000),'published',tenant_id,organization_id,workspace_id,project_id,created_by FROM tools ON CONFLICT DO NOTHING;

DO $$ DECLARE t TEXT; BEGIN FOREACH t IN ARRAY ARRAY['prompt_versions','prompt_experiments','prompt_experiment_variants','tool_input_contracts','tool_contract_test_cases'] LOOP
 EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
 EXECUTE format('CREATE POLICY tenant_scope_policy ON %I USING(COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)) WITH CHECK(COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting(''app.tenant_id'',true),'''')::BIGINT,0))',t);
 END LOOP;END $$;
CREATE INDEX idx_prompt_experiment_resolve ON prompt_experiments(base_prompt_id,agent_id,stage,status);
CREATE INDEX idx_tool_input_contracts_current ON tool_input_contracts(tool_id,status,created_at DESC);
COMMENT ON TABLE prompt_versions IS 'Prompt 不可变内容版本';COMMENT ON TABLE prompt_experiments IS 'Prompt 稳定分流实验定义';COMMENT ON TABLE prompt_experiment_variants IS 'Prompt 实验候选 Variant';
COMMENT ON TABLE tool_input_contracts IS 'Tool 输入 Schema 版本、长度和敏感字段契约';COMMENT ON TABLE tool_contract_test_cases IS 'Tool Contract 回归样例';
