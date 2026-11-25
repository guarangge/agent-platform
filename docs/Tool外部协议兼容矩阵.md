# Tool 外部协议兼容矩阵

## 判定标准

每个已注册 Tool 均通过 `/api/core/tools/{toolId}/compatibility-runs` 执行同一套四阶段测试：`configuration -> connectivity/initialize -> discovery -> invocation`。只有真实调用成功才标记为 `compatible`；连接成功但发现或调用失败标记为 `partially_compatible`；无法连接标记为 `incompatible`。结果写入 `tool_protocol_compatibility_runs`，全局最新矩阵通过 `/api/core/tools/compatibility-matrix` 查询。

## 当前协议基线

| Provider | Transport | 初始化/连接 | 发现 | 调用 | 自动化协议测试 |
| --- | --- | --- | --- | --- | --- |
| `mcp` | `streamable_http` | MCP initialize、initialized、session | `tools/list` | `tools/call`，JSON/SSE 响应 | 平台 E2E + `McpJsonRpcResponseParserTest` |
| `mcp` | `stdio` | JSON Lines 子进程 initialize | `tools/list` | `tools/call` | `McpStdioTransportTest` |
| `mcp` | `sse` / `legacy_sse` | HTTP+SSE 双通道 initialize | `tools/list` | `tools/call` | `McpLegacySseTransportTest` |
| `external_http` | `http_get` | HEAD/GET health | 不适用，视为通过 | query/path 参数调用 | Compatibility Suite 真实调用阶段 |
| `external_http` | `http_post` | HEAD/GET health | 不适用，视为通过 | JSON body 调用 | Compatibility Suite 真实调用阶段 |
| `external_http` | `sse` | HEAD/GET health | 不适用，视为通过 | SSE data 聚合 | Compatibility Suite 真实调用阶段 |

矩阵是实例级实际结果，不将“代码声明支持”当作兼容。供应商、版本、认证和网络环境变化后必须重新执行，并保留历史记录。
