-- 注册平台通用内置 Tool，并补齐 Provider、实例、绑定、版本和部署生命周期。
INSERT INTO tool_providers (
    provider_code, provider_name, provider_type, transport_type, config_schema, status, metadata, remark
) VALUES (
    'internal_platform_provider', '平台内置 Tool Provider', 'internal', 'in_process', '{}'::jsonb,
    'enabled', '{"managedBy":"platform"}'::jsonb, '平台通用内置 Tool'
)
ON CONFLICT (provider_code) DO UPDATE SET
    provider_name = EXCLUDED.provider_name,
    provider_type = EXCLUDED.provider_type,
    transport_type = EXCLUDED.transport_type,
    status = 'enabled',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_provider_instances (
    provider_id, instance_code, instance_name, environment_code, endpoint, auth_type, auth_config,
    headers, timeout_seconds, retry_times, status, metadata, remark
)
SELECT id, 'internal_platform_default', '平台内置默认实例', 'default', NULL, 'none', '{}'::jsonb,
       '{}'::jsonb, 60, 0, 'enabled', '{"managedBy":"platform"}'::jsonb, '进程内执行实例'
FROM tool_providers WHERE provider_code = 'internal_platform_provider' AND is_deleted = FALSE
ON CONFLICT (instance_code) DO UPDATE SET
    provider_id = EXCLUDED.provider_id,
    instance_name = EXCLUDED.instance_name,
    status = 'enabled',
    updated_at = CURRENT_TIMESTAMP;

WITH definitions(tool_code, tool_name, tool_type, tool_category, description, input_schema, permission_level,
                 timeout_seconds, metadata) AS (
    VALUES
    ('TIME_INFO', '时间信息', 'system', 'platform_default', '查询指定 IANA 时区的当前日期、时间和时区信息',
     '{"type":"object","properties":{"zoneId":{"type":"string","description":"IANA时区，如Asia/Shanghai"}}}'::jsonb,
     'default', 10, '{"capabilities":["time","date","timezone"],"readOnly":true}'::jsonb),
    ('CALCULATOR', '安全计算器', 'analysis', 'platform_default', '执行确定性的十进制四则运算、取模和括号表达式',
     '{"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}'::jsonb,
     'default', 10, '{"capabilities":["calculation"],"readOnly":true}'::jsonb),
    ('KNOWLEDGE_SEARCH', '知识库检索', 'knowledge', 'platform_default', '在 Agent 已授权知识库内执行关键词与向量混合检索并返回引用',
     '{"type":"object","properties":{"query":{"type":"string"},"knowledgeId":{"type":"integer"},"topK":{"type":"integer"}},"required":["query"]}'::jsonb,
     'default', 30, '{"capabilities":["knowledge_retrieval","citation"],"readOnly":true}'::jsonb),
    ('HTTP_FETCH', 'HTTP 内容读取', 'http', 'general', '通过受控 GET 请求读取公共网络内容，阻断本地和私有网络地址',
     '{"type":"object","properties":{"url":{"type":"string"},"accept":{"type":"string"},"maxBytes":{"type":"integer"}},"required":["url"]}'::jsonb,
     'restricted', 30, '{"capabilities":["http_fetch"],"readOnly":true}'::jsonb),
    ('WEB_SEARCH', 'Web 搜索', 'http', 'general', '通过可配置搜索 Provider 检索公共 Web 信息',
     '{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query"]}'::jsonb,
     'restricted', 30, '{"capabilities":["web_search"],"readOnly":true,"allowedHosts":["api.duckduckgo.com"]}'::jsonb),
    ('DOCUMENT_PARSE', '文档解析', 'file', 'document', '解析 PDF、Office、HTML 和纯文本并提取正文与元数据',
     '{"type":"object","properties":{"fileName":{"type":"string"},"contentBase64":{"type":"string"},"maxChars":{"type":"integer"}},"required":["contentBase64"]}'::jsonb,
     'restricted', 60, '{"capabilities":["document_parse","pdf","office"],"readOnly":true,"maxInputBytes":10485760}'::jsonb),
    ('WORKSPACE_FILE', '会话工作区文件', 'file', 'general', '在用户、Agent、会话隔离的持久工作区内列举、读取、写入和删除文件',
     '{"type":"object","properties":{"operation":{"type":"string","enum":["list","read","write","delete","mkdir"]},"path":{"type":"string"},"content":{"type":"string"},"overwrite":{"type":"boolean"}},"required":["operation"]}'::jsonb,
     'restricted', 30, '{"capabilities":["workspace_file"],"workspaceScoped":true}'::jsonb),
    ('SANDBOX_EXECUTE', 'Sandbox 隔离执行', 'system', 'general', '在受资源限制的 Docker Sandbox 中执行白名单命令并保留审计记录',
     '{"type":"object","properties":{"profileCode":{"type":"string"},"command":{"type":"array","items":{"type":"string"}},"inputFiles":{"type":"array","items":{"type":"object","properties":{"path":{"type":"string"},"contentBase64":{"type":"string"}},"required":["path","contentBase64"]}},"artifactPaths":{"type":"array","items":{"type":"string"}}},"required":["command"]}'::jsonb,
     'admin', 90, '{"capabilities":["code_execute","sandbox"],"isolated":true}'::jsonb)
)
INSERT INTO tools (
    tool_code, tool_name, tool_type, tool_category, description, input_schema, output_schema,
    permission_level, timeout_seconds, status, metadata, remark
)
SELECT tool_code, tool_name, tool_type, tool_category, description, input_schema,
       '{"type":"object"}'::jsonb, permission_level, timeout_seconds, 'enabled', metadata,
       '平台通用内置 Tool'
FROM definitions
ON CONFLICT (tool_code) DO UPDATE SET
    tool_name = EXCLUDED.tool_name,
    tool_type = EXCLUDED.tool_type,
    tool_category = EXCLUDED.tool_category,
    description = EXCLUDED.description,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    permission_level = EXCLUDED.permission_level,
    timeout_seconds = EXCLUDED.timeout_seconds,
    status = 'enabled',
    metadata = EXCLUDED.metadata,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_bindings (
    tool_id, provider_id, provider_instance_id, operation_name, operation_type,
    invoke_config, argument_mapping, output_mapping, status, metadata, remark
)
SELECT t.id, p.id, i.id, lower(t.tool_code), 'internal_method',
       CASE WHEN t.tool_code = 'WEB_SEARCH'
            THEN '{"urlTemplate":"https://api.duckduckgo.com/?q={query}&format=json&no_html=1&skip_disambig=1"}'::jsonb
            ELSE '{}'::jsonb END,
       CASE WHEN t.tool_code = 'CALCULATOR' THEN '{"rename":{"inputText":"expression"}}'::jsonb
            ELSE '{}'::jsonb END,
       '{}'::jsonb, 'enabled', '{"managedBy":"platform"}'::jsonb, '平台通用内置绑定'
FROM tools t
JOIN tool_providers p ON p.provider_code = 'internal_platform_provider' AND p.is_deleted = FALSE
JOIN tool_provider_instances i ON i.instance_code = 'internal_platform_default' AND i.is_deleted = FALSE
WHERE t.tool_code IN ('TIME_INFO','CALCULATOR','KNOWLEDGE_SEARCH','HTTP_FETCH','WEB_SEARCH',
                      'DOCUMENT_PARSE','WORKSPACE_FILE','SANDBOX_EXECUTE')
  AND t.is_deleted = FALSE
ON CONFLICT (tool_id, is_deleted) DO UPDATE SET
    provider_id = EXCLUDED.provider_id,
    provider_instance_id = EXCLUDED.provider_instance_id,
    operation_name = EXCLUDED.operation_name,
    operation_type = EXCLUDED.operation_type,
    invoke_config = EXCLUDED.invoke_config,
    argument_mapping = EXCLUDED.argument_mapping,
    output_mapping = EXCLUDED.output_mapping,
    status = 'enabled',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_versions (
    tool_id, version, input_schema, output_schema, invoke_config, release_notes,
    compatibility, status, published_at
)
SELECT t.id, '1.0.0', t.input_schema, t.output_schema, b.invoke_config,
       '平台通用内置 Tool 首次正式发布', 'compatible', 'published', CURRENT_TIMESTAMP
FROM tools t
JOIN tool_bindings b ON b.tool_id = t.id AND b.is_deleted = FALSE
WHERE t.tool_code IN ('TIME_INFO','CALCULATOR','KNOWLEDGE_SEARCH','HTTP_FETCH','WEB_SEARCH',
                      'DOCUMENT_PARSE','WORKSPACE_FILE','SANDBOX_EXECUTE')
  AND t.is_deleted = FALSE
ON CONFLICT (tool_id, version) WHERE is_deleted = FALSE DO UPDATE SET
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    invoke_config = EXCLUDED.invoke_config,
    status = 'published',
    published_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_deployments (
    tool_id, version_id, deployment_code, deployment_name, environment_code, provider_type,
    transport_type, endpoint, auth_type, auth_config, headers, invoke_config, traffic_weight,
    rollout_percent, priority, timeout_seconds, retry_times, failure_threshold, recovery_seconds,
    status, metadata
)
SELECT t.id, v.id, lower(t.tool_code) || '_internal_v1', t.tool_name || '内置部署', 'default',
       'internal', 'in_process', NULL, 'none', '{}'::jsonb, '{}'::jsonb, v.invoke_config,
       100, 100, 100, t.timeout_seconds, 0, 3, 60, 'enabled', '{"managedBy":"platform"}'::jsonb
FROM tools t
JOIN tool_versions v ON v.tool_id = t.id AND v.version = '1.0.0' AND v.is_deleted = FALSE
WHERE t.tool_code IN ('TIME_INFO','CALCULATOR','KNOWLEDGE_SEARCH','HTTP_FETCH','WEB_SEARCH',
                      'DOCUMENT_PARSE','WORKSPACE_FILE','SANDBOX_EXECUTE')
  AND t.is_deleted = FALSE
ON CONFLICT (deployment_code) WHERE is_deleted = FALSE DO UPDATE SET
    version_id = EXCLUDED.version_id,
    deployment_name = EXCLUDED.deployment_name,
    invoke_config = EXCLUDED.invoke_config,
    timeout_seconds = EXCLUDED.timeout_seconds,
    status = 'enabled',
    updated_at = CURRENT_TIMESTAMP;
