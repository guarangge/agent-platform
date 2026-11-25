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
    ('tool_provider_type', 'Tool Provider 类型', 'enabled', TRUE, 'Tool Provider 协议类型字典', '{}'::jsonb, '升级脚本'),
    ('tool_transport_type', 'Tool Transport 类型', 'enabled', TRUE, 'Tool Provider 传输协议字典', '{}'::jsonb, '升级脚本'),
    ('tool_auth_type', 'Tool 认证类型', 'enabled', TRUE, 'Tool Provider 实例认证方式字典', '{}'::jsonb, '升级脚本'),
    ('tool_operation_type', 'Tool 操作类型', 'enabled', TRUE, 'Tool 绑定操作类型字典', '{}'::jsonb, '升级脚本')
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
SELECT t.id, v.item_code, v.item_name, v.sort_order, 'enabled', v.is_default, v.description, '{}'::jsonb, '升级脚本'
FROM dict_types t
JOIN (
    VALUES
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
        ('tool_operation_type', 'http_api', 'HTTP API', 30, FALSE, '调用外部 HTTP API')
) AS v(type_code, item_code, item_name, sort_order, is_default, description)
    ON v.type_code = t.type_code
WHERE NOT EXISTS (
    SELECT 1
    FROM dict_items di
    WHERE di.type_id = t.id
      AND di.item_code = v.item_code
      AND di.is_deleted = FALSE
);

COMMENT ON TABLE tool_providers IS 'Tool Provider 定义表';
COMMENT ON COLUMN tool_providers.id IS '主键ID';
COMMENT ON COLUMN tool_providers.provider_code IS 'Provider 编码';
COMMENT ON COLUMN tool_providers.provider_name IS 'Provider 名称';
COMMENT ON COLUMN tool_providers.provider_type IS 'Provider 类型';
COMMENT ON COLUMN tool_providers.transport_type IS '传输协议类型';
COMMENT ON COLUMN tool_providers.config_schema IS 'Provider 配置结构定义';
COMMENT ON COLUMN tool_providers.status IS '状态';
COMMENT ON COLUMN tool_providers.metadata IS '扩展元数据';
COMMENT ON COLUMN tool_providers.remark IS '备注';
COMMENT ON COLUMN tool_providers.created_at IS '创建时间';
COMMENT ON COLUMN tool_providers.created_by IS '创建人';
COMMENT ON COLUMN tool_providers.updated_at IS '更新时间';
COMMENT ON COLUMN tool_providers.updated_by IS '更新人';
COMMENT ON COLUMN tool_providers.is_deleted IS '逻辑删除标记';

COMMENT ON TABLE tool_provider_instances IS 'Tool Provider 实例表';
COMMENT ON COLUMN tool_provider_instances.id IS '主键ID';
COMMENT ON COLUMN tool_provider_instances.provider_id IS 'Provider ID';
COMMENT ON COLUMN tool_provider_instances.instance_code IS '实例编码';
COMMENT ON COLUMN tool_provider_instances.instance_name IS '实例名称';
COMMENT ON COLUMN tool_provider_instances.environment_code IS '环境编码';
COMMENT ON COLUMN tool_provider_instances.endpoint IS '服务地址或入口';
COMMENT ON COLUMN tool_provider_instances.auth_type IS '认证类型';
COMMENT ON COLUMN tool_provider_instances.auth_config IS '认证配置';
COMMENT ON COLUMN tool_provider_instances.headers IS '默认请求头';
COMMENT ON COLUMN tool_provider_instances.timeout_seconds IS '超时时间秒数';
COMMENT ON COLUMN tool_provider_instances.retry_times IS '重试次数';
COMMENT ON COLUMN tool_provider_instances.status IS '状态';
COMMENT ON COLUMN tool_provider_instances.metadata IS '扩展元数据';
COMMENT ON COLUMN tool_provider_instances.remark IS '备注';
COMMENT ON COLUMN tool_provider_instances.created_at IS '创建时间';
COMMENT ON COLUMN tool_provider_instances.created_by IS '创建人';
COMMENT ON COLUMN tool_provider_instances.updated_at IS '更新时间';
COMMENT ON COLUMN tool_provider_instances.updated_by IS '更新人';
COMMENT ON COLUMN tool_provider_instances.is_deleted IS '逻辑删除标记';

COMMENT ON TABLE tool_bindings IS 'Tool 绑定表';
COMMENT ON COLUMN tool_bindings.id IS '主键ID';
COMMENT ON COLUMN tool_bindings.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_bindings.provider_id IS 'Provider ID';
COMMENT ON COLUMN tool_bindings.provider_instance_id IS 'Provider 实例 ID';
COMMENT ON COLUMN tool_bindings.operation_name IS '目标操作名称';
COMMENT ON COLUMN tool_bindings.operation_type IS '目标操作类型';
COMMENT ON COLUMN tool_bindings.invoke_config IS '调用配置';
COMMENT ON COLUMN tool_bindings.argument_mapping IS '参数映射配置';
COMMENT ON COLUMN tool_bindings.output_mapping IS '输出映射配置';
COMMENT ON COLUMN tool_bindings.status IS '状态';
COMMENT ON COLUMN tool_bindings.metadata IS '扩展元数据';
COMMENT ON COLUMN tool_bindings.remark IS '备注';
COMMENT ON COLUMN tool_bindings.created_at IS '创建时间';
COMMENT ON COLUMN tool_bindings.created_by IS '创建人';
COMMENT ON COLUMN tool_bindings.updated_at IS '更新时间';
COMMENT ON COLUMN tool_bindings.updated_by IS '更新人';
COMMENT ON COLUMN tool_bindings.is_deleted IS '逻辑删除标记';
