-- 修复历史 Catalog 安装 Tool 缺少运行时能力声明的问题，防止写操作被只读基线放行。
UPDATE tools t
SET runtime_capabilities = '["filesystem.write"]'::jsonb,
    side_effect_level = 'irreversible',
    metadata = jsonb_set(
        COALESCE(t.metadata, '{}'::jsonb),
        '{sideEffectPolicy}',
        '{"level":"irreversible","idempotencyMode":"generated"}'::jsonb,
        TRUE
    ),
    updated_at = CURRENT_TIMESTAMP
FROM tool_bindings tb
JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
WHERE tb.tool_id = t.id
  AND tb.is_deleted = FALSE
  AND t.is_deleted = FALSE
  AND LOWER(tp.provider_type) = 'mcp'
  AND LOWER(tb.operation_name) IN (
      'write_file', 'edit_file', 'move_file', 'rename_file',
      'delete_file', 'remove_file', 'create_directory', 'delete_directory', 'remove_directory'
  )
  AND t.runtime_capabilities = '[]'::jsonb
  AND t.side_effect_level = 'none';

-- 对无法映射到具体边界、但名称明确表示变更的 MCP operation 使用外部副作用兜底。
UPDATE tools t
SET runtime_capabilities = '["external.side_effect"]'::jsonb,
    side_effect_level = 'irreversible',
    metadata = jsonb_set(
        COALESCE(t.metadata, '{}'::jsonb),
        '{sideEffectPolicy}',
        '{"level":"irreversible","idempotencyMode":"generated"}'::jsonb,
        TRUE
    ),
    updated_at = CURRENT_TIMESTAMP
FROM tool_bindings tb
JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
WHERE tb.tool_id = t.id
  AND tb.is_deleted = FALSE
  AND t.is_deleted = FALSE
  AND LOWER(tp.provider_type) = 'mcp'
  AND LOWER(tb.operation_name) ~ '(^|_)(write|edit|create|update|upsert|delete|remove|move|rename|publish|upload|send|execute|run|post|put|patch)($|_)'
  AND t.runtime_capabilities = '[]'::jsonb
  AND t.side_effect_level = 'none';
