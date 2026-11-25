CREATE TABLE agent_worker_groups (
    id BIGSERIAL PRIMARY KEY,
    group_code VARCHAR(128) NOT NULL,
    group_name VARCHAR(200) NOT NULL,
    max_concurrency INTEGER NOT NULL DEFAULT 32,
    queue_capacity INTEGER NOT NULL DEFAULT 1000,
    required_capabilities JSONB NOT NULL DEFAULT '[]'::JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_worker_groups_scope UNIQUE (tenant_id, project_id, group_code),
    CONSTRAINT ck_agent_worker_groups_capacity CHECK (max_concurrency > 0 AND queue_capacity > 0),
    CONSTRAINT ck_agent_worker_groups_status CHECK (status IN ('enabled','draining','disabled'))
);

CREATE TABLE agent_worker_nodes (
    id BIGSERIAL PRIMARY KEY,
    worker_id VARCHAR(200) NOT NULL,
    group_code VARCHAR(128) NOT NULL,
    instance_address VARCHAR(300),
    capabilities JSONB NOT NULL DEFAULT '[]'::JSONB,
    max_concurrency INTEGER NOT NULL,
    current_load INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'online',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_worker_nodes_scope UNIQUE (tenant_id, project_id, worker_id),
    CONSTRAINT ck_agent_worker_nodes_load CHECK (max_concurrency > 0 AND current_load >= 0),
    CONSTRAINT ck_agent_worker_nodes_status CHECK (status IN ('online','draining','offline','unhealthy'))
);

CREATE TABLE agent_run_routing_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(128) NOT NULL,
    rule_name VARCHAR(200) NOT NULL,
    agent_id BIGINT,
    min_priority INTEGER,
    max_priority INTEGER,
    required_capabilities JSONB NOT NULL DEFAULT '[]'::JSONB,
    target_group_code VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_routing_rules_scope UNIQUE (tenant_id, project_id, rule_code),
    CONSTRAINT ck_agent_run_routing_rules_priority CHECK (priority >= 0 AND min_priority >= 0 AND max_priority <= 100),
    CONSTRAINT ck_agent_run_routing_rules_status CHECK (status IN ('enabled','disabled'))
);

INSERT INTO agent_worker_groups(group_code,group_name,max_concurrency,queue_capacity,required_capabilities,status)
VALUES ('default','默认 Worker 组',32,1000,'[]','enabled') ON CONFLICT DO NOTHING;

ALTER TABLE agent_runs ADD COLUMN worker_group_code VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE agent_runs ADD COLUMN required_capabilities JSONB NOT NULL DEFAULT '[]'::JSONB;
CREATE INDEX idx_agent_runs_group_claim ON agent_runs(worker_group_code,status,available_at,priority DESC,id) WHERE is_deleted=FALSE;

CREATE INDEX idx_agent_worker_nodes_group_heartbeat ON agent_worker_nodes(group_code,status,last_heartbeat_at DESC);
CREATE INDEX idx_agent_run_routing_rules_match ON agent_run_routing_rules(agent_id,status,priority,id);

ALTER TABLE agent_worker_groups ENABLE ROW LEVEL SECURITY; ALTER TABLE agent_worker_groups FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_worker_nodes ENABLE ROW LEVEL SECURITY; ALTER TABLE agent_worker_nodes FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_run_routing_rules ENABLE ROW LEVEL SECURITY; ALTER TABLE agent_run_routing_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON agent_worker_groups USING (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));
CREATE POLICY tenant_scope_policy ON agent_worker_nodes USING (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));
CREATE POLICY tenant_scope_policy ON agent_run_routing_rules USING (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)) WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0)=0 OR tenant_id=COALESCE(NULLIF(current_setting('app.tenant_id',true),'')::BIGINT,0));

COMMENT ON TABLE agent_worker_groups IS 'Agent Run Worker 分组容量与能力要求';
COMMENT ON TABLE agent_worker_nodes IS 'Worker 节点注册、心跳、负载和下线状态';
COMMENT ON TABLE agent_run_routing_rules IS '按 Agent 和优先级路由异步 Run 到 Worker Group 的规则';
COMMENT ON COLUMN agent_runs.worker_group_code IS '调度目标 Worker Group 编码';
COMMENT ON COLUMN agent_runs.required_capabilities IS '运行所需 Worker 能力标签 JSON 数组';
