-- 预置可复用 MCP 连接模板。模板只保存环境变量占位符，不保存实际凭据。
INSERT INTO tool_catalogs (
    catalog_code, catalog_name, catalog_type, source_url, transport_type, config, status
) VALUES
(
    'mcp_filesystem_template', 'Filesystem MCP 模板', 'mcp_server', NULL, 'stdio',
    replace('{
      "template": true,
      "description": "受目录边界约束的文件系统 MCP；MCP_FILESYSTEM_ROOT 必须指向专用工作区",
      "requiredEnvironmentVariables": ["MCP_FILESYSTEM_ROOT"],
      "discovery": {
        "providerType": "mcp",
        "transportType": "stdio",
        "authType": "none",
        "endpoint": "",
        "authConfig": {},
        "headers": {},
        "invokeConfig": {
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-filesystem", "__MCP_FILESYSTEM_ROOT__"],
          "inheritEnvironment": true
        }
      },
      "securityPolicy": {"scope": "workspace_only", "approvalRequiredForWrite": true}
    }', '__MCP_FILESYSTEM_ROOT__', '$' || '{MCP_FILESYSTEM_ROOT}')::jsonb,
    'enabled'
),
(
    'mcp_postgres_template', 'PostgreSQL MCP 模板', 'mcp_server', NULL, 'stdio',
    replace('{
      "template": true,
      "description": "PostgreSQL MCP；必须使用只读账号和独立数据库授权",
      "requiredEnvironmentVariables": ["MCP_POSTGRES_READONLY_URL"],
      "discovery": {
        "providerType": "mcp",
        "transportType": "stdio",
        "authType": "none",
        "endpoint": "",
        "authConfig": {},
        "headers": {},
        "invokeConfig": {
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-postgres", "__MCP_POSTGRES_READONLY_URL__"],
          "inheritEnvironment": true
        }
      },
      "securityPolicy": {"databaseAccess": "read_only", "ddlBlocked": true, "dmlBlocked": true}
    }', '__MCP_POSTGRES_READONLY_URL__', '$' || '{MCP_POSTGRES_READONLY_URL}')::jsonb,
    'enabled'
),
(
    'mcp_github_template', 'GitHub MCP 模板', 'mcp_server', 'https://api.githubcopilot.com/mcp/', 'streamable_http',
    replace('{
      "template": true,
      "description": "GitHub 官方远程 MCP；默认建议绑定只读 Toolset",
      "requiredEnvironmentVariables": ["GITHUB_MCP_TOKEN"],
      "discovery": {
        "providerType": "mcp",
        "transportType": "streamable_http",
        "authType": "bearer",
        "endpoint": "https://api.githubcopilot.com/mcp/",
        "authConfig": {"bearerToken": "__GITHUB_MCP_TOKEN__", "token": "__GITHUB_MCP_TOKEN__"},
        "headers": {},
        "invokeConfig": {}
      },
      "securityPolicy": {"recommendedMode": "read_only", "approvalRequiredForWrite": true}
    }', '__GITHUB_MCP_TOKEN__', '$' || '{GITHUB_MCP_TOKEN}')::jsonb,
    'enabled'
)
ON CONFLICT (catalog_code) WHERE is_deleted = FALSE DO UPDATE SET
    catalog_name = EXCLUDED.catalog_name,
    catalog_type = EXCLUDED.catalog_type,
    source_url = EXCLUDED.source_url,
    transport_type = EXCLUDED.transport_type,
    config = EXCLUDED.config,
    status = 'enabled',
    updated_at = CURRENT_TIMESTAMP;
