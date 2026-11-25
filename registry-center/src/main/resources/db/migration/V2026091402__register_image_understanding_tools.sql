-- 注册图片理解插件 Tool；图片正文仅由 agent-core 在调用期按 attachmentId 读取。
WITH definitions(tool_code, tool_name, description, input_schema, output_schema) AS (
    VALUES
    ('IMAGE_OCR', '图片文字识别', '识别当前消息图片中的文字并返回自然阅读顺序正文',
     '{"type":"object","properties":{"attachmentId":{"type":"integer"},"prompt":{"type":"string"},"maxChars":{"type":"integer","minimum":100,"maximum":100000}}}'::jsonb,
     '{"type":"object","properties":{"attachmentId":{"type":"integer"},"fileName":{"type":"string"},"mimeType":{"type":"string"},"sha256":{"type":"string"},"text":{"type":"string"},"truncated":{"type":"boolean"},"providerModel":{"type":"string"}},"required":["attachmentId","mimeType","sha256","text","truncated"]}'::jsonb),
    ('IMAGE_LAYOUT_ANALYZE', '图片版面分析', '读取当前消息图片的尺寸、方向和宽高比，不上传到外部服务',
     '{"type":"object","properties":{"attachmentId":{"type":"integer"}}}'::jsonb,
     '{"type":"object","properties":{"attachmentId":{"type":"integer"},"fileName":{"type":"string"},"mimeType":{"type":"string"},"sha256":{"type":"string"},"width":{"type":"integer"},"height":{"type":"integer"},"orientation":{"type":"string","enum":["portrait","landscape","square"]},"aspectRatio":{"type":"number"}},"required":["attachmentId","mimeType","sha256","width","height","orientation","aspectRatio"]}'::jsonb)
)
INSERT INTO tools (
    tool_code, tool_name, tool_type, tool_category, description, input_schema, output_schema,
    permission_level, timeout_seconds, status, metadata, runtime_capabilities, side_effect_level, remark
)
SELECT tool_code, tool_name, 'image', 'multimodal', description, input_schema, output_schema,
       'restricted', 60, 'enabled',
       '{"managedBy":"image-understanding-plugin","readOnly":true,"inputModality":"image"}'::jsonb,
       '["filesystem.read"]'::jsonb, 'none', '图片理解 Runtime Plugin Tool'
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
    runtime_capabilities = EXCLUDED.runtime_capabilities,
    side_effect_level = EXCLUDED.side_effect_level,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_bindings (
    tool_id, provider_id, provider_instance_id, operation_name, operation_type,
    invoke_config, argument_mapping, output_mapping, status, metadata, remark
)
SELECT t.id, p.id, i.id, lower(t.tool_code), 'internal_method', '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
       'enabled', '{"pluginCode":"image-understanding-plugin"}'::jsonb, '图片理解插件进程内绑定'
FROM tools t
JOIN tool_providers p ON p.provider_code = 'internal_platform_provider' AND p.is_deleted = FALSE
JOIN tool_provider_instances i ON i.instance_code = 'internal_platform_default' AND i.is_deleted = FALSE
WHERE t.tool_code IN ('IMAGE_OCR', 'IMAGE_LAYOUT_ANALYZE') AND t.is_deleted = FALSE
ON CONFLICT (tool_id, is_deleted) DO UPDATE SET
    provider_id = EXCLUDED.provider_id,
    provider_instance_id = EXCLUDED.provider_instance_id,
    operation_name = EXCLUDED.operation_name,
    operation_type = EXCLUDED.operation_type,
    status = 'enabled',
    metadata = EXCLUDED.metadata,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_versions (
    tool_id, version, input_schema, output_schema, invoke_config, release_notes,
    compatibility, status, published_at
)
SELECT t.id, '1.0.0', t.input_schema, t.output_schema, '{}'::jsonb,
       '图片理解插件首次发布', 'compatible', 'published', CURRENT_TIMESTAMP
FROM tools t
WHERE t.tool_code IN ('IMAGE_OCR', 'IMAGE_LAYOUT_ANALYZE') AND t.is_deleted = FALSE
ON CONFLICT (tool_id, version) WHERE is_deleted = FALSE DO UPDATE SET
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    status = 'published',
    published_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tool_deployments (
    tool_id, version_id, deployment_code, deployment_name, environment_code, provider_type,
    transport_type, endpoint, auth_type, auth_config, headers, invoke_config, traffic_weight,
    rollout_percent, priority, timeout_seconds, retry_times, failure_threshold, recovery_seconds,
    status, metadata
)
SELECT t.id, v.id, lower(t.tool_code) || '_image_plugin_v1', t.tool_name || '插件部署', 'default',
       'internal', 'in_process', NULL, 'none', '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
       100, 100, 100, t.timeout_seconds, 0, 3, 60, 'enabled',
       '{"pluginCode":"image-understanding-plugin"}'::jsonb
FROM tools t
JOIN tool_versions v ON v.tool_id = t.id AND v.version = '1.0.0' AND v.is_deleted = FALSE
WHERE t.tool_code IN ('IMAGE_OCR', 'IMAGE_LAYOUT_ANALYZE') AND t.is_deleted = FALSE
ON CONFLICT (deployment_code) WHERE is_deleted = FALSE DO UPDATE SET
    version_id = EXCLUDED.version_id,
    deployment_name = EXCLUDED.deployment_name,
    timeout_seconds = EXCLUDED.timeout_seconds,
    status = 'enabled',
    metadata = EXCLUDED.metadata,
    updated_at = CURRENT_TIMESTAMP;
