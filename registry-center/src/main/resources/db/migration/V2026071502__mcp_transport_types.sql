INSERT INTO dict_items (
    type_id, item_code, item_name, sort_order, status, is_default,
    description, metadata, created_at, updated_at, created_by, updated_by, is_deleted
)
SELECT dt.id, item.item_code, item.item_name, item.sort_order, 'enabled', FALSE,
       item.description, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1, FALSE
FROM dict_types dt
JOIN (
    VALUES
        ('tool_transport_type', 'stdio', 'MCP Stdio', 40, '启动本地子进程并通过标准输入输出执行 MCP JSON-RPC'),
        ('tool_transport_type', 'sse', 'MCP Legacy SSE', 50, '通过 legacy HTTP+SSE 长连接执行 MCP JSON-RPC')
) AS item(type_code, item_code, item_name, sort_order, description)
    ON item.type_code = dt.type_code
WHERE dt.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM dict_items existing
      WHERE existing.type_id = dt.id
        AND existing.item_code = item.item_code
        AND existing.is_deleted = FALSE
  );
