CREATE TABLE prompt_experiment_exposures(
 id BIGSERIAL PRIMARY KEY,experiment_id BIGINT NOT NULL,experiment_code VARCHAR(128) NOT NULL,variant_id BIGINT NOT NULL,variant_code VARCHAR(64) NOT NULL,
 base_prompt_id BIGINT NOT NULL,selected_prompt_id BIGINT NOT NULL,stage VARCHAR(32) NOT NULL,user_id BIGINT,agent_id BIGINT,conversation_id BIGINT,task_id BIGINT,
 outcome_status VARCHAR(32),quality_score NUMERIC(8,2),duration_ms BIGINT,cost_amount NUMERIC(18,6),metadata JSONB NOT NULL DEFAULT '{}',exposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1),
 CONSTRAINT uk_prompt_exposure_task_stage UNIQUE(task_id,stage)
);
CREATE TABLE tool_input_observations(
 id BIGSERIAL PRIMARY KEY,tool_code VARCHAR(128) NOT NULL,contract_version VARCHAR(64),provider_type VARCHAR(64),transport_type VARCHAR(64),
 user_id BIGINT,agent_id BIGINT,conversation_id BIGINT,task_id BIGINT,raw_input_chars INTEGER NOT NULL,final_input_chars INTEGER NOT NULL,
 payload_hash VARCHAR(64),schema_valid BOOLEAN NOT NULL,clamped BOOLEAN NOT NULL,sensitive_paths_masked JSONB NOT NULL DEFAULT '[]',status VARCHAR(32) NOT NULL,error_code VARCHAR(128),duration_ms BIGINT,metadata JSONB NOT NULL DEFAULT '{}',observed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id',true),''),'0')::BIGINT,1),organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id',true),'')::BIGINT,1),workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id',true),'')::BIGINT,1),project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id',true),'')::BIGINT,1)
);
CREATE INDEX idx_prompt_exposures_metrics ON prompt_experiment_exposures(experiment_id,variant_id,exposed_at DESC);
CREATE INDEX idx_tool_input_observations_metrics ON tool_input_observations(tool_code,status,observed_at DESC);
ALTER TABLE prompt_experiment_exposures ENABLE ROW LEVEL SECURITY;ALTER TABLE prompt_experiment_exposures FORCE ROW LEVEL SECURITY;ALTER TABLE tool_input_observations ENABLE ROW LEVEL SECURITY;ALTER TABLE tool_input_observations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON prompt_experiment_exposures USING(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));
CREATE POLICY tenant_scope_policy ON tool_input_observations USING(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK(COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));
COMMENT ON TABLE prompt_experiment_exposures IS 'Prompt 实验曝光、结果、质量、时延和成本归因';COMMENT ON TABLE tool_input_observations IS 'Tool 实际输入契约校验、裁剪、脱敏与执行结果观测';
