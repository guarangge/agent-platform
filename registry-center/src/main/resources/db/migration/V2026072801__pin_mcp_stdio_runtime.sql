-- stdio MCP 由 Runtime 镜像预装固定版本，避免每次发现或调用时由 npx 临时下载不确定版本。
UPDATE tool_catalogs
SET config = jsonb_set(
        jsonb_set(config, '{discovery,invokeConfig,command}', '"mcp-server-filesystem"'::jsonb, TRUE),
        '{discovery,invokeConfig,args}',
        jsonb_build_array('$' || '{MCP_FILESYSTEM_ROOT}'),
        TRUE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE catalog_code = 'mcp_filesystem_template'
  AND is_deleted = FALSE;

UPDATE tool_catalogs
SET config = jsonb_set(
        jsonb_set(config, '{discovery,invokeConfig,command}', '"mcp-server-postgres"'::jsonb, TRUE),
        '{discovery,invokeConfig,args}',
        jsonb_build_array('$' || '{MCP_POSTGRES_READONLY_URL}'),
        TRUE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE catalog_code = 'mcp_postgres_template'
  AND is_deleted = FALSE;
