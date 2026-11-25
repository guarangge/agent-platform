# 通用 Tool 与 MCP 接入

## 1. 内置 Tool

平台当前提供 8 个真实内置 Tool，全部通过 Registry 定义和 `agent-core` 的 `ToolRouterService` 执行，不绕过 Schema、Guardrail、Approval、Trace、熔断与成本治理。

| Tool Code | 能力 | 默认继承 | 权限 |
| --- | --- | --- | --- |
| `TIME_INFO` | IANA 时区日期时间 | 是 | `default` |
| `CALCULATOR` | 安全十进制表达式计算 | 是 | `default` |
| `KNOWLEDGE_SEARCH` | Agent/角色授权知识库混合检索 | 是 | `default` |
| `HTTP_FETCH` | 受控公共 HTTP GET，阻断本地和私网 | 否 | `restricted` |
| `WEB_SEARCH` | 可配置 Web Search Provider | 否 | `restricted` |
| `DOCUMENT_PARSE` | PDF、Office、HTML、文本解析 | 否 | `restricted` |
| `WORKSPACE_FILE` | 用户/Agent/会话隔离工作区 | 否 | `restricted` |
| `SANDBOX_EXECUTE` | Docker Sandbox 白名单命令执行 | 否 | `admin` |

预置 Capability Pack：

1. `BASIC_REASONING`：`TIME_INFO + CALCULATOR`
2. `RESEARCH_RETRIEVAL`：`KNOWLEDGE_SEARCH + WEB_SEARCH + HTTP_FETCH`
3. `DOCUMENT_WORKSPACE`：`DOCUMENT_PARSE + WORKSPACE_FILE`
4. `CONTROLLED_EXECUTION`：`SANDBOX_EXECUTE`，必须结合 Approval Policy 使用

Agent 页面“Tool 范围”中可同时配置显式 Tool 和多个 Capability Pack。运行时取范围 Tool 与能力包 Tool 的去重并集。

## 2. MCP 模板

Tool 生命周期抽屉的“安装与发现”页预置以下模板：

| 模板 | Transport | 必需环境变量 | 安全要求 |
| --- | --- | --- | --- |
| Filesystem MCP | `stdio` | `MCP_FILESYSTEM_ROOT` | 只指向 Agent 专用目录，写操作需审批 |
| PostgreSQL MCP | `stdio` | `MCP_POSTGRES_READONLY_URL` | 必须使用数据库只读账号，不授予 DDL/DML |
| GitHub MCP | `streamable_http` | `GITHUB_MCP_TOKEN` | 使用最小权限 Token，写 Tool 需审批 |

Docker / Linux 环境示例：

```bash
export MCP_FILESYSTEM_ROOT=/workspace/agent-files
export MCP_POSTGRES_READONLY_URL=postgresql://agent_reader:password@postgres:5432/app
export GITHUB_MCP_TOKEN=github_pat_xxx
```

变量必须配置在 `registry-center` 和实际执行 MCP 的 `agent-core` 容器环境中。数据库只保存 `${ENV_VAR}` 占位符；发现或调用时变量缺失会返回明确错误，不会降级为明文或空凭据。

`docker/runtime/Dockerfile` 已固定安装 Node.js `22.22.0`、Filesystem MCP `2026.7.10` 和 PostgreSQL MCP `0.6.2`。模板直接调用 `mcp-server-filesystem / mcp-server-postgres`，不在发现或正式执行时临时下载依赖。Registry 发现侧与 Core 运行侧按完整命令精确匹配白名单；生产环境可分别通过 `REGISTRY_MCP_ALLOWED_STDIO_COMMANDS` 与 `CORE_TOOL_MCP_ALLOWED_STDIO_COMMANDS` 继续收紧。

本地 Docker 环境在项目根目录执行：

```bash
./scripts/deploy/recreate_local_agent_services.sh
```

脚本会执行以下动作：

1. 创建并持久化 `.runtime/mcp-filesystem`、`.runtime/agent-workspace` 和 `.runtime/sandbox-workspace`
2. Docker Socket 部署必须同时设置 `CORE_SANDBOX_WORKSPACE_ROOT`（容器内路径）和 `CORE_SANDBOX_HOST_WORKSPACE_ROOT`（宿主机真实路径），Sandbox 子容器挂载时会按相对路径转换，避免 Docker Desktop 报 `mounts denied`
3. 初始化独立的 `agent_mcp_demo` 数据库和只读账号，不向 MCP 暴露平台业务数据库
4. 构建固定版本 Runtime 镜像并重建 `agent-core / registry-center`
5. 将相同的 MCP 环境变量和 Filesystem 目录挂载到发现端与执行端
6. 等待两个服务通过 Actuator 健康检查

GitHub MCP 仅透传宿主机的 `GITHUB_MCP_TOKEN`，脚本不会生成 Token，也不会把 Token 写入数据库或仓库。

当前本地实例已完成以下验收：

1. Filesystem MCP 发现 14 个远程 Tool，并完成真实写入、读取验证
2. PostgreSQL MCP 发现 `query`，完成真实 SELECT，并确认 INSERT 被只读事务拒绝
3. GitHub MCP 未配置 `GITHUB_MCP_TOKEN` 时明确拒绝发现，不使用空凭据降级连接

在新数据库或需要重新对齐配置时，在项目根目录执行幂等 provisioning：

```bash
./scripts/deploy/provision_general_agent_mcp.sh
```

可通过 `REGISTRY_URL / REGISTRY_ADMIN_USERNAME / REGISTRY_ADMIN_PASSWORD / GENERAL_AGENT_CODE` 覆盖本地默认值。脚本会更新能力包和审批策略，不重复安装已有 Tool。

## 3. 接入顺序

1. 在 Tool 生命周期页选择模板并填写运行环境变量
2. 执行远程 `tools/list` 发现
3. 选择远程 Tool 安装，确认 `authType`、分类、权限等级和版本
4. 执行连接、初始化、发现、真实调用四阶段兼容性验证
5. 将 Tool 加入 Capability Pack，或直接绑定 Agent
6. 对网络、写入、数据库和代码执行能力配置 Guardrail 与 Approval Policy
7. 从 Trace、健康检查和成本页持续观察运行结果

## 4. 端到端验收基线

2026-07-28 已在本地 Docker 实例完成通用 Agent、Capability Pack、Approval Policy 和 MCP 的完整运行时验收：

1. PostgreSQL MCP 通过真实 `query` Tool 查询 `platform_capabilities`，Plan 中的 Tool 与 Reply 步骤均执行成功
2. PostgreSQL MCP 使用独立只读账号，执行 `INSERT` 时由 PostgreSQL 返回 `cannot execute INSERT in a read-only transaction`
3. Filesystem MCP 写操作命中 Guardrail 后进入 Approval，批准后从持久化 Checkpoint 恢复且只执行一次写入
4. 写入结果已在宿主持久目录 `.runtime/mcp-filesystem/approval-schema.txt` 验证，内容与请求一致
5. 已安装的 Filesystem 与 PostgreSQL MCP Tool 健康检查 `15/15` 通过
6. 验收任务同时产生 Guardrail 命中、Approval、Tool Invoke、Step Execution、Task Result 和 Response Assembly Trace
7. GitHub MCP 在缺少真实 `GITHUB_MCP_TOKEN` 时保持 fail-closed；配置最小权限 Token 后再执行发现、兼容性和读写审批验收

运行时同时遵守以下安全约束：

1. Planner 降级只选择最高优先级的一个 Tool 和一个 Workflow，不批量执行所有候选资源
2. Tool fallback 只有在 `operationName` 或显式 `fallbackGroup` 兼容且输入 Schema 可满足时才允许切换
3. Approval 恢复使用稳定的 Step ID、类型和资源标识校验，不依赖可能变化的展示名称
4. 已批准的 Guardrail 只消费当前步骤的一次审批授权，防止重复审批或越过后续步骤策略
