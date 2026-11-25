CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    mobile VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    last_login_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    menu_scope JSONB NOT NULL DEFAULT '[]'::jsonb,
    description VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_role_code ON roles (role_code);
CREATE INDEX IF NOT EXISTS idx_roles_status ON roles (status);

CREATE TABLE IF NOT EXISTS user_role_rel (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_role_rel_user_role_deleted ON user_role_rel (user_id, role_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_user_role_rel_user_id ON user_role_rel (user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_rel_role_id ON user_role_rel (role_id);

CREATE TABLE IF NOT EXISTS agents (
    id BIGSERIAL PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    version VARCHAR(32) NOT NULL DEFAULT 'v1.0',
    default_model VARCHAR(64),
    default_prompt_id BIGINT,
    decision_prompt_id BIGINT,
    planner_prompt_id BIGINT,
    tool_scope_mode VARCHAR(32) NOT NULL DEFAULT 'custom',
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agents_agent_code ON agents (agent_code);
CREATE INDEX IF NOT EXISTS idx_agents_status ON agents (status);

ALTER TABLE agents DROP COLUMN IF EXISTS workflow_id;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS decision_prompt_id BIGINT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS planner_prompt_id BIGINT;

CREATE TABLE IF NOT EXISTS models (
    id BIGSERIAL PRIMARY KEY,
    model_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(255),
    api_key VARCHAR(1200),
    api_path VARCHAR(255),
    model_identifier VARCHAR(128),
    temperature DOUBLE PRECISION,
    max_tokens INTEGER,
    context_window INTEGER,
    reserved_output_tokens INTEGER,
    reserved_system_tokens INTEGER,
    compaction_trigger_ratio DOUBLE PRECISION,
    tokenizer_family VARCHAR(64),
    timeout_seconds INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_models_model_code ON models (model_code);
CREATE INDEX IF NOT EXISTS idx_models_status ON models (status);
CREATE INDEX IF NOT EXISTS idx_models_provider ON models (provider);
CREATE INDEX IF NOT EXISTS idx_models_model_type ON models (model_type);

CREATE TABLE IF NOT EXISTS tools (
    id BIGSERIAL PRIMARY KEY,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_type VARCHAR(64) NOT NULL,
    tool_category VARCHAR(64) NOT NULL DEFAULT 'general',
    description VARCHAR(500),
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    permission_level VARCHAR(32) NOT NULL DEFAULT 'default',
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE tools ADD COLUMN IF NOT EXISTS tool_category VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tools_tool_code ON tools (tool_code);
CREATE INDEX IF NOT EXISTS idx_tools_status ON tools (status);
CREATE INDEX IF NOT EXISTS idx_tools_tool_type ON tools (tool_type);
CREATE INDEX IF NOT EXISTS idx_tools_tool_category ON tools (tool_category);

CREATE TABLE IF NOT EXISTS tool_providers (
    id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    transport_type VARCHAR(64) NOT NULL DEFAULT 'in_process',
    config_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_providers_provider_code ON tool_providers (provider_code);
CREATE INDEX IF NOT EXISTS idx_tool_providers_provider_type ON tool_providers (provider_type);
CREATE INDEX IF NOT EXISTS idx_tool_providers_status ON tool_providers (status);

CREATE TABLE IF NOT EXISTS tool_provider_instances (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    instance_code VARCHAR(64) NOT NULL,
    instance_name VARCHAR(128) NOT NULL,
    environment_code VARCHAR(64) NOT NULL DEFAULT 'default',
    endpoint VARCHAR(500),
    auth_type VARCHAR(64) NOT NULL DEFAULT 'none',
    auth_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    retry_times INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_provider_instances_instance_code ON tool_provider_instances (instance_code);
CREATE INDEX IF NOT EXISTS idx_tool_provider_instances_provider_id ON tool_provider_instances (provider_id);
CREATE INDEX IF NOT EXISTS idx_tool_provider_instances_environment_code ON tool_provider_instances (environment_code);
CREATE INDEX IF NOT EXISTS idx_tool_provider_instances_status ON tool_provider_instances (status);

CREATE TABLE IF NOT EXISTS tool_bindings (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_instance_id BIGINT NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    invoke_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    argument_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_bindings_tool_deleted ON tool_bindings (tool_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_tool_bindings_provider_id ON tool_bindings (provider_id);
CREATE INDEX IF NOT EXISTS idx_tool_bindings_provider_instance_id ON tool_bindings (provider_instance_id);
CREATE INDEX IF NOT EXISTS idx_tool_bindings_status ON tool_bindings (status);

CREATE TABLE IF NOT EXISTS workflows (
    id BIGSERIAL PRIMARY KEY,
    workflow_code VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(128) NOT NULL,
    workflow_type VARCHAR(64) NOT NULL DEFAULT 'orchestration',
    workflow_category VARCHAR(64) NOT NULL DEFAULT 'general',
    description VARCHAR(500),
    version VARCHAR(32) NOT NULL DEFAULT 'v1.0',
    definition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE workflows ADD COLUMN IF NOT EXISTS workflow_type VARCHAR(64);
ALTER TABLE workflows ADD COLUMN IF NOT EXISTS workflow_category VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflows_workflow_code ON workflows (workflow_code);
CREATE INDEX IF NOT EXISTS idx_workflows_status ON workflows (status);
CREATE INDEX IF NOT EXISTS idx_workflows_version ON workflows (version);
CREATE INDEX IF NOT EXISTS idx_workflows_workflow_type ON workflows (workflow_type);
CREATE INDEX IF NOT EXISTS idx_workflows_workflow_category ON workflows (workflow_category);

CREATE TABLE IF NOT EXISTS prompts (
    id BIGSERIAL PRIMARY KEY,
    prompt_code VARCHAR(64) NOT NULL,
    prompt_name VARCHAR(128) NOT NULL,
    prompt_type VARCHAR(64) NOT NULL,
    prompt_category VARCHAR(64) NOT NULL DEFAULT 'general',
    description VARCHAR(500),
    content TEXT NOT NULL,
    version VARCHAR(32) NOT NULL DEFAULT 'v1.0',
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE prompts ADD COLUMN IF NOT EXISTS prompt_category VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_prompts_prompt_code ON prompts (prompt_code);
CREATE INDEX IF NOT EXISTS idx_prompts_status ON prompts (status);
CREATE INDEX IF NOT EXISTS idx_prompts_prompt_type ON prompts (prompt_type);
CREATE INDEX IF NOT EXISTS idx_prompts_prompt_category ON prompts (prompt_category);

CREATE TABLE IF NOT EXISTS dict_types (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_types_type_code ON dict_types (type_code);
CREATE INDEX IF NOT EXISTS idx_dict_types_status ON dict_types (status);

CREATE TABLE IF NOT EXISTS dict_items (
    id BIGSERIAL PRIMARY KEY,
    type_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_items_type_item_deleted ON dict_items (type_id, item_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_dict_items_type_id ON dict_items (type_id);
CREATE INDEX IF NOT EXISTS idx_dict_items_status ON dict_items (status);
CREATE INDEX IF NOT EXISTS idx_dict_items_sort_order ON dict_items (sort_order);

CREATE TABLE IF NOT EXISTS guardrail_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    policy_version INTEGER NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    description VARCHAR(500),
    rollout_mode VARCHAR(32) NOT NULL DEFAULT 'all',
    rollout_percentage INTEGER,
    rollout_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_role_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_agent_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_tool_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_workflow_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_envs JSONB NOT NULL DEFAULT '[]'::jsonb,
    match_mode VARCHAR(32) NOT NULL DEFAULT 'always',
    require_non_empty_input BOOLEAN NOT NULL DEFAULT FALSE,
    max_input_length INTEGER,
    blocked_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    action_type VARCHAR(32) NOT NULL DEFAULT 'block',
    approval_node_name VARCHAR(128),
    approval_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    approval_role_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_guardrail_policies_policy_code ON guardrail_policies (policy_code);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_status ON guardrail_policies (status);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_action_type ON guardrail_policies (action_type);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_priority ON guardrail_policies (priority);

COMMENT ON TABLE guardrail_policies IS 'Guardrail 策略中心表';
COMMENT ON COLUMN guardrail_policies.id IS '主键ID';
COMMENT ON COLUMN guardrail_policies.policy_code IS '策略编码';
COMMENT ON COLUMN guardrail_policies.policy_name IS '策略名称';
COMMENT ON COLUMN guardrail_policies.policy_version IS '策略版本号';
COMMENT ON COLUMN guardrail_policies.priority IS '优先级，值越大优先级越高';
COMMENT ON COLUMN guardrail_policies.status IS '状态 draft/enabled/disabled';
COMMENT ON COLUMN guardrail_policies.description IS '策略描述';
COMMENT ON COLUMN guardrail_policies.rollout_mode IS '灰度模式 all/percentage/user_whitelist';
COMMENT ON COLUMN guardrail_policies.rollout_percentage IS '灰度百分比';
COMMENT ON COLUMN guardrail_policies.rollout_user_ids IS '灰度白名单用户ID列表';
COMMENT ON COLUMN guardrail_policies.scope_user_ids IS '作用域用户ID列表';
COMMENT ON COLUMN guardrail_policies.scope_role_codes IS '作用域角色编码列表';
COMMENT ON COLUMN guardrail_policies.scope_agent_ids IS '作用域Agent ID列表';
COMMENT ON COLUMN guardrail_policies.scope_tool_ids IS '作用域Tool ID列表';
COMMENT ON COLUMN guardrail_policies.scope_workflow_ids IS '作用域Workflow ID列表';
COMMENT ON COLUMN guardrail_policies.scope_envs IS '作用域环境列表';
COMMENT ON COLUMN guardrail_policies.match_mode IS '匹配模式 always/keyword_match';
COMMENT ON COLUMN guardrail_policies.require_non_empty_input IS '是否要求输入非空';
COMMENT ON COLUMN guardrail_policies.max_input_length IS '最大输入长度';
COMMENT ON COLUMN guardrail_policies.blocked_keywords IS '阻断关键词列表';
COMMENT ON COLUMN guardrail_policies.keywords IS '审批/匹配关键词列表';
COMMENT ON COLUMN guardrail_policies.action_type IS '动作类型 block/approval';
COMMENT ON COLUMN guardrail_policies.approval_node_name IS '审批节点名称';
COMMENT ON COLUMN guardrail_policies.approval_user_ids IS '审批指定用户ID列表';
COMMENT ON COLUMN guardrail_policies.approval_role_codes IS '审批指定角色编码列表';
COMMENT ON COLUMN guardrail_policies.reason IS '命中原因说明';
COMMENT ON COLUMN guardrail_policies.metadata IS '扩展元数据';
COMMENT ON COLUMN guardrail_policies.remark IS '备注';
COMMENT ON COLUMN guardrail_policies.created_at IS '创建时间';
COMMENT ON COLUMN guardrail_policies.created_by IS '创建人';
COMMENT ON COLUMN guardrail_policies.updated_at IS '更新时间';
COMMENT ON COLUMN guardrail_policies.updated_by IS '更新人';
COMMENT ON COLUMN guardrail_policies.is_deleted IS '逻辑删除标记';

UPDATE tools
SET tool_category = COALESCE(NULLIF(tool_category, ''), 'general')
WHERE tool_category IS NULL OR tool_category = '';

UPDATE workflows
SET workflow_type = COALESCE(NULLIF(workflow_type, ''), 'orchestration'),
    workflow_category = COALESCE(NULLIF(workflow_category, ''), 'general')
WHERE workflow_type IS NULL
   OR workflow_type = ''
   OR workflow_category IS NULL
   OR workflow_category = '';

UPDATE prompts
SET prompt_type = CASE
    WHEN prompt_type = 'system' THEN 'agent_system'
    WHEN prompt_type = 'workflow' THEN 'workflow_node'
    ELSE prompt_type
END
WHERE prompt_type IN ('system', 'workflow');

UPDATE prompts
SET prompt_category = COALESCE(NULLIF(prompt_category, ''), 'general')
WHERE prompt_category IS NULL OR prompt_category = '';

CREATE TABLE IF NOT EXISTS agent_tool_rel (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool_rel_agent_tool_deleted ON agent_tool_rel (agent_id, tool_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_agent_tool_rel_agent_id ON agent_tool_rel (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_rel_tool_id ON agent_tool_rel (tool_id);

CREATE TABLE IF NOT EXISTS agent_workflow_rel (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    workflow_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_workflow_rel_agent_workflow_deleted
    ON agent_workflow_rel (agent_id, workflow_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_rel_agent_id ON agent_workflow_rel (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_rel_workflow_id ON agent_workflow_rel (workflow_id);

CREATE TABLE IF NOT EXISTS role_agent_rel (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO dict_types (
    type_code,
    type_name,
    status,
    built_in,
    description,
    metadata,
    remark
)
VALUES
    ('tool_type', 'Tool 类型', 'enabled', TRUE, 'Tool 能力类型字典', '{}'::jsonb, '初始化数据'),
    ('tool_category', 'Tool 分类', 'enabled', TRUE, 'Tool 场景分类字典', '{}'::jsonb, '初始化数据'),
    ('tool_provider_type', 'Tool Provider 类型', 'enabled', TRUE, 'Tool Provider 协议类型字典', '{}'::jsonb, '初始化数据'),
    ('tool_transport_type', 'Tool Transport 类型', 'enabled', TRUE, 'Tool Provider 传输协议字典', '{}'::jsonb, '初始化数据'),
    ('tool_auth_type', 'Tool 认证类型', 'enabled', TRUE, 'Tool Provider 实例认证方式字典', '{}'::jsonb, '初始化数据'),
    ('tool_operation_type', 'Tool 操作类型', 'enabled', TRUE, 'Tool 绑定操作类型字典', '{}'::jsonb, '初始化数据'),
    ('workflow_type', 'Workflow 类型', 'enabled', TRUE, 'Workflow 能力类型字典', '{}'::jsonb, '初始化数据'),
    ('workflow_category', 'Workflow 分类', 'enabled', TRUE, 'Workflow 场景分类字典', '{}'::jsonb, '初始化数据'),
    ('model_provider', '模型供应商', 'enabled', TRUE, '模型供应商字典', '{}'::jsonb, '初始化数据'),
    ('model_type', '模型类型', 'enabled', TRUE, '模型类型字典', '{}'::jsonb, '初始化数据'),
    ('prompt_type', 'Prompt 类型', 'enabled', TRUE, 'Prompt 类型字典', '{}'::jsonb, '初始化数据'),
    ('prompt_category', 'Prompt 分类', 'enabled', TRUE, 'Prompt 场景分类字典', '{}'::jsonb, '初始化数据'),
    ('permission_level', '权限等级', 'enabled', TRUE, 'Tool 权限等级字典', '{}'::jsonb, '初始化数据'),
    ('agent_tool_scope_mode', 'Agent Tool 范围模式', 'enabled', TRUE, 'Agent Tool 范围模式字典', '{}'::jsonb, '初始化数据')
ON CONFLICT (type_code) DO NOTHING;

INSERT INTO dict_items (
    type_id,
    item_code,
    item_name,
    sort_order,
    status,
    is_default,
    description,
    metadata,
    remark
)
SELECT t.id, v.item_code, v.item_name, v.sort_order, 'enabled', v.is_default, v.description, '{}'::jsonb, '初始化数据'
FROM dict_types t
JOIN (
    VALUES
        ('tool_type', 'search', '检索类', 10, FALSE, '检索和搜索相关 Tool'),
        ('tool_type', 'knowledge', '知识类', 20, FALSE, '知识读取和知识检索 Tool'),
        ('tool_type', 'analysis', '分析类', 30, TRUE, '分析和推理类 Tool'),
        ('tool_type', 'file', '文件类', 40, FALSE, '文件读写处理 Tool'),
        ('tool_type', 'http', '网络类', 50, FALSE, 'HTTP / API 调用 Tool'),
        ('tool_type', 'system', '系统类', 60, FALSE, '系统级 Tool'),
        ('tool_category', 'platform_default', '平台默认', 10, TRUE, '平台内置默认 Tool'),
        ('tool_category', 'general', '通用', 20, FALSE, '通用场景 Tool'),
        ('tool_category', 'education', '教育', 30, FALSE, '教育行业 Tool'),
        ('tool_category', 'github', 'GitHub', 40, FALSE, 'GitHub 场景 Tool'),
        ('tool_category', 'document', '文档', 50, FALSE, '文档处理 Tool'),
        ('tool_category', 'office', '办公', 60, FALSE, 'Office 场景 Tool'),
        ('tool_provider_type', 'internal', '内置 Provider', 10, TRUE, '由 agent-core 本地执行的内置 Tool'),
        ('tool_provider_type', 'mcp', 'MCP Provider', 20, FALSE, '通过 MCP 协议接入的 Tool'),
        ('tool_provider_type', 'external_http', '外部 HTTP Provider', 30, FALSE, '通过 HTTP / API 接入的 Tool'),
        ('tool_transport_type', 'in_process', '进程内', 10, TRUE, '本地进程内直接执行'),
        ('tool_transport_type', 'streamable_http', 'Streamable HTTP', 20, FALSE, '通过 HTTP 与 MCP / 外部服务交互'),
        ('tool_transport_type', 'http_post', 'HTTP POST', 30, FALSE, '通过 HTTP POST 调用外部接口'),
        ('tool_transport_type', 'stdio', 'MCP Stdio', 40, FALSE, '启动本地子进程并通过标准输入输出执行 MCP JSON-RPC'),
        ('tool_transport_type', 'sse', 'MCP Legacy SSE', 50, FALSE, '通过 legacy HTTP+SSE 长连接执行 MCP JSON-RPC'),
        ('tool_auth_type', 'none', '无认证', 10, TRUE, '无需认证'),
        ('tool_auth_type', 'bearer', 'Bearer Token', 20, FALSE, 'Authorization Bearer Token'),
        ('tool_auth_type', 'api_key', 'API Key', 30, FALSE, '通过 Header 或 Query 传递 API Key'),
        ('tool_auth_type', 'basic', 'Basic Auth', 40, FALSE, 'HTTP Basic 认证'),
        ('tool_auth_type', 'custom_header', '自定义 Header', 50, FALSE, '通过自定义 Header 传递认证信息'),
        ('tool_operation_type', 'internal_method', '内部方法', 10, TRUE, '调用本地内置 Tool 实现'),
        ('tool_operation_type', 'mcp_tool', 'MCP Tool', 20, FALSE, '调用 MCP 服务暴露的 Tool'),
        ('tool_operation_type', 'http_api', 'HTTP API', 30, FALSE, '调用外部 HTTP API'),
        ('workflow_type', 'orchestration', '编排类', 10, TRUE, '通用编排流程'),
        ('workflow_type', 'planning', '规划类', 20, FALSE, '任务规划流程'),
        ('workflow_type', 'chat', '对话类', 30, FALSE, '问答对话流程'),
        ('workflow_type', 'approval', '审批类', 40, FALSE, '人工审批流程'),
        ('workflow_type', 'analysis', '分析类', 50, FALSE, '分析推理流程'),
        ('workflow_category', 'platform_default', '平台默认', 10, TRUE, '平台内置默认 Workflow'),
        ('workflow_category', 'general', '通用', 20, FALSE, '通用场景 Workflow'),
        ('workflow_category', 'education', '教育', 30, FALSE, '教育行业 Workflow'),
        ('workflow_category', 'github', 'GitHub', 40, FALSE, 'GitHub 场景 Workflow'),
        ('workflow_category', 'document', '文档', 50, FALSE, '文档处理 Workflow'),
        ('model_provider', 'openai', 'OpenAI 官方', 10, TRUE, 'OpenAI 官方 API'),
        ('model_provider', 'openai_compatible', 'OpenAI 兼容', 20, FALSE, '兼容 OpenAI 协议的模型服务'),
        ('model_provider', 'anthropic', 'Anthropic', 30, FALSE, 'Anthropic 模型服务'),
        ('model_provider', 'google', 'Google', 40, FALSE, 'Google 模型服务'),
        ('model_provider', 'azure_openai', 'Azure OpenAI', 50, FALSE, 'Azure OpenAI 模型服务'),
        ('model_type', 'chat', '对话模型', 10, TRUE, '对话与推理类模型'),
        ('model_type', 'embedding', '向量模型', 20, FALSE, 'Embedding 向量模型'),
        ('model_type', 'rerank', '重排模型', 30, FALSE, 'Rerank 重排模型'),
        ('model_type', 'vision', '视觉模型', 40, FALSE, '多模态视觉模型'),
        ('prompt_type', 'agent_system', 'Agent 系统提示词', 10, TRUE, 'Agent 主执行 Prompt'),
        ('prompt_type', 'decision_router', '决策路由提示词', 20, FALSE, 'Decision 阶段路由 Prompt'),
        ('prompt_type', 'planner_system', '任务规划提示词', 30, FALSE, 'Planner 阶段任务拆解 Prompt'),
        ('prompt_type', 'workflow_node', 'Workflow 节点提示词', 40, FALSE, 'Workflow 节点执行 Prompt'),
        ('prompt_type', 'tool_execution', 'Tool 执行提示词', 50, FALSE, 'Tool 执行 Prompt'),
        ('prompt_type', 'memory_summary', '记忆整理提示词', 60, FALSE, 'Memory 提炼与归纳 Prompt'),
        ('prompt_type', 'assistant', '助手提示词', 70, FALSE, '助手回答 Prompt'),
        ('prompt_type', 'task', '任务提示词', 80, FALSE, '任务执行 Prompt'),
        ('prompt_type', 'workflow', '流程提示词', 90, FALSE, '兼容旧 Workflow Prompt'),
        ('prompt_category', 'platform_default', '平台默认', 10, TRUE, '平台内置默认 Prompt'),
        ('prompt_category', 'general', '通用', 20, FALSE, '通用场景 Prompt'),
        ('prompt_category', 'education', '教育', 30, FALSE, '教育行业 Prompt'),
        ('prompt_category', 'github', 'GitHub', 40, FALSE, 'GitHub 场景 Prompt'),
        ('prompt_category', 'document', '文档', 50, FALSE, '文档处理 Prompt'),
        ('permission_level', 'default', '默认权限', 10, TRUE, '默认可用权限'),
        ('permission_level', 'restricted', '受限权限', 20, FALSE, '需额外确认或授权'),
        ('permission_level', 'admin', '管理员权限', 30, FALSE, '仅管理员可用'),
        ('agent_tool_scope_mode', 'inherit_default', '仅继承默认', 10, TRUE, '仅使用平台默认 Tool 集'),
        ('agent_tool_scope_mode', 'inherit_and_append', '继承后追加', 20, FALSE, '继承平台默认 Tool 后追加 Agent Tool'),
        ('agent_tool_scope_mode', 'custom', '完全自定义', 30, FALSE, '仅使用 Agent 自定义 Tool')
) AS v(type_code, item_code, item_name, sort_order, is_default, description)
    ON v.type_code = t.type_code
WHERE NOT EXISTS (
    SELECT 1
    FROM dict_items i
    WHERE i.type_id = t.id
      AND i.item_code = v.item_code
      AND i.is_deleted = FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_agent_rel_role_agent_deleted ON role_agent_rel (role_id, agent_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_role_agent_rel_role_id ON role_agent_rel (role_id);
CREATE INDEX IF NOT EXISTS idx_role_agent_rel_agent_id ON role_agent_rel (agent_id);

INSERT INTO roles (
    role_code,
    role_name,
    status,
    menu_scope,
    description,
    metadata,
    remark
)
VALUES (
    'ADMIN',
    '平台管理员',
    'enabled',
    '["dashboard","model","agent","tool","workflow","prompt","user","role","dict","chat"]'::jsonb,
    '系统初始化管理员角色',
    '{}'::jsonb,
    '初始化数据'
)
ON CONFLICT (role_code) DO NOTHING;

UPDATE roles
SET menu_scope = (
    SELECT CASE
               WHEN menu_scope ? 'model' AND menu_scope ? 'dict' THEN menu_scope
               WHEN menu_scope ? 'model' THEN menu_scope || '"dict"'::jsonb
               WHEN menu_scope ? 'dict' THEN menu_scope || '"model"'::jsonb
               ELSE menu_scope || '["model","dict"]'::jsonb
           END
)
WHERE role_code = 'ADMIN'
  AND is_deleted = FALSE;

INSERT INTO users (
    username,
    password_hash,
    display_name,
    status,
    metadata,
    remark
)
VALUES (
    'zc',
    '$2a$10$g7ZIXZGvNUKQfSnj/.3iOujZHuFsjZSgPDC85j.hQG/otQKpaOIeC',
    '张晨',
    'enabled',
    '{}'::jsonb,
    '初始化演示用户'
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role_rel (
    user_id,
    role_id,
    status
)
SELECT u.id, r.id, 'enabled'
FROM users u
JOIN roles r ON r.role_code = 'ADMIN'
WHERE u.username = 'zc'
ON CONFLICT (user_id, role_id, is_deleted) DO NOTHING;

INSERT INTO models (
    model_code,
    model_name,
    provider,
    model_type,
    base_url,
    api_key,
    api_path,
    model_identifier,
    temperature,
    max_tokens,
    context_window,
    reserved_output_tokens,
    reserved_system_tokens,
    compaction_trigger_ratio,
    tokenizer_family,
    timeout_seconds,
    status,
    metadata,
    remark
)
VALUES
    (
        'gpt-4o-mini',
        'GPT-4o Mini',
        'openai',
        'chat',
        'https://api.openai.com',
        NULL,
        '/v1/chat/completions',
        'gpt-4o-mini',
        0.2,
        4096,
        128000,
        4096,
        2048,
        0.8,
        'cl100k_base',
        60,
        'enabled',
        '{}'::jsonb,
        '初始化基础模型'
    )
ON CONFLICT (model_code) DO NOTHING;
