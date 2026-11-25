# Capability Policy 规范

## 1. 当前阶段

M4 Capability Policy 第四个纵向切片已完成：在调用前确定性策略决策和稳定 Tool Call 关联基础上，将 `policy.decided` 作为全量决策审计事实源，提供分页、指标、聚合和关联事件详情查询。

当前已覆盖声明、策略决策、调用前阻断、会话内审批恢复、稳定 Tool Call 关联、Runtime Event 和全量决策运营审计。Capability Policy 当前范围完成。

## 2. Runtime Capability

首批只允许以下九个稳定编码：

| 编码 | 边界 |
| --- | --- |
| `filesystem.read` | 读取文件或目录 |
| `filesystem.write` | 创建、修改或删除文件 |
| `network.read` | 通过网络读取外部信息 |
| `network.write` | 通过网络提交或修改外部信息 |
| `process.execute` | 启动进程或执行命令 |
| `database.read` | 查询数据库 |
| `database.write` | 修改数据库 |
| `artifact.publish` | 将内部产物发布为可下载或外部可访问资源 |
| `external.side_effect` | 触发无法由以上分类准确表达的外部状态变化 |

约定：

1. Tool 使用 `tools.runtime_capabilities` 声明，API 字段为 `runtimeCapabilities`
2. Runtime Plugin 使用 Manifest `capabilities` 声明
3. 未知、空白和重复编码在 Registry 写入或插件加载前被拒绝
4. `metadata.routing.capabilities` 是给 Planner 使用的业务能力说明，不能作为安全授权依据
5. Capability 声明必须覆盖实现可能触达的最大边界，实际策略可按本次参数进一步收窄

## 3. Tool Side Effect

`tools.side_effect_level` 是运行时权威副作用等级：

1. `none`：无外部状态变化
2. `idempotent`：存在副作用，但相同幂等键可安全复用结果
3. `compensatable`：存在副作用，并配置可执行的补偿 Tool
4. `irreversible`：存在不可安全自动补偿的副作用

`metadata.sideEffectPolicy` 继续保存执行细节：

```json
{
  "sideEffectPolicy": {
    "level": "compensatable",
    "idempotencyMode": "required_custom",
    "compensationToolCode": "ORDER_CANCEL"
  }
}
```

Registry 强制顶层 `sideEffectLevel` 与 `metadata.sideEffectPolicy.level` 一致。Core 以顶层字段为准，只有读取升级前的历史执行快照时才回退到旧 metadata。

## 4. 内置 Tool 初始声明

| Tool | Runtime Capability | Side Effect |
| --- | --- | --- |
| `TIME_INFO`、`CALCULATOR` | 无 | `none` |
| `KNOWLEDGE_SEARCH` | `database.read` | `none` |
| `HTTP_FETCH`、`WEB_SEARCH` | `network.read` | `none` |
| `DOCUMENT_PARSE` | `filesystem.read` | `none` |
| `WORKSPACE_FILE` | `filesystem.read`、`filesystem.write` | `idempotent` |
| `SANDBOX_EXECUTE` | `process.execute`、`filesystem.read`、`filesystem.write` | `none`，因为副作用隔离在 Sandbox 内 |

## 5. 决策与执行边界

决策顺序如下：

1. 校验 Registry 下发的 `runtimeCapabilities` 和 `sideEffectLevel`
2. 对 Tool 输入文本和规范化后的结构化参数计算 SHA-256 `parameterHash`
3. 按优先级匹配 Registry 生效策略中的 Capability Policy
4. 未命中显式策略时执行平台安全基线
5. 在副作用幂等占用和 Tool Provider 调用前发布 `policy.decided` 并强制执行结果

平台安全基线：

| 条件 | 决策 |
| --- | --- |
| 无受控能力或仅只读能力 | `allow` |
| `SANDBOX_EXECUTE`，或声明 `isolated/workspaceScoped` 的无副作用进程、文件写入 | `sandbox` |
| 外部写入、数据库写入、产物发布、外部副作用、非隔离进程执行，或副作用等级不为 `none` | `approval` |
| 非法 Capability / Side Effect 声明，或要求 Sandbox 但 Tool 未声明隔离边界 | `block` |

`sandbox` 表示本次调用只允许沿 Tool 已声明的隔离边界继续执行，不会把任意 Provider 动态改造成 Sandbox。Planner 生成的步骤名称、风险文案和参数中的自报风险字段不参与降低权限。

## 6. 显式策略配置

Capability Policy 复用 Registry 的 Guardrail Policy 资源，但必须在“扩展 Metadata”中明确标记策略类型。示例：

```json
{
  "policyType": "capability",
  "stage": "input",
  "capabilityPolicy": {
    "capabilities": ["filesystem.write"],
    "sideEffectLevels": ["irreversible"],
    "projectIds": [1],
    "parameterConditions": [
      {
        "path": "/operation",
        "operator": "in",
        "values": ["delete"]
      }
    ],
    "targetPaths": ["/path"],
    "targetPrefixes": ["/protected"]
  }
}
```

规则约定：

1. Policy 原有 Scope 继续约束用户、角色、Agent、Tool 和环境，Rollout 继续支持全量、白名单和百分比灰度
2. `capabilities`、`sideEffectLevels` 和 `projectIds` 为空时不限制对应维度
3. 参数路径支持 JSON Pointer（如 `/operation`）和点路径，操作符支持 `exists / equals / in / prefix`
4. `targetPaths` 只负责从参数中提取目标；`targetPrefixes` 用于前缀匹配
5. 决策事件和审批元数据只保存参数哈希、目标哈希和目标数量，不保存 Tool 参数或目标资源原文
6. Capability Policy 只允许 `allow / sandbox / approval / block`；普通文本 Guardrail 不执行 `allow / sandbox`

## 7. 审批恢复

1. `approval` 使用策略编码、策略版本、能力声明、副作用等级和参数哈希生成稳定审批节点 ID
2. 对话窗口继续消费统一 `approval.requested`，不把等待审批状态写成聊天回复
3. 审批通过后仅对同一步骤、同一稳定节点授予一次调用权限；参数或策略版本变化会生成不同节点
4. Plan 和 Workflow 恢复都重试当前 Tool 节点，不重复已成功步骤
5. Capability 审批恢复会跳过同一节点已通过的前置 Guardrail，避免多类审批互相循环
6. 首次决策在 Provider 调用前分配 `toolCallId`，并与 `parameterHash` 一起进入审批元数据
7. 续跑时只有审批节点和参数哈希均一致才复用原 `toolCallId`；历史审批缺少该字段时兼容生成新 ID

## 8. Runtime Event

`policy.decided` 在 Provider 调用前写入，包含 `toolCallId`、Tool、策略、动作、Capability、Side Effect、`parameterHash`、`targetHash`、稳定审批节点和授权状态。事件写入失败时不执行 Provider，避免出现“行为已发生但策略事实缺失”。

Capability 的 `approval.requested / approval.resolved` 额外投影经过格式校验的 `toolCallId` 和 `parameterHash`；完整审批元数据、参数值、目标路径及审批意见均不会进入事件。`policy.decided` 与后续 `tool.call.*` 在统一事件查询中映射为同一个 `itemId`。

## 9. 全量决策运营审计

1. `GET /api/core/capability-policy/audits` 按当前用户与 Tenant / Organization / Workspace / Project 作用域隔离查询全部 `policy.decided`，支持决策、Tool、Capability、Side Effect、策略、Agent、Run、Trace 和时间范围筛选
2. `GET /api/core/capability-policy/audits/metrics` 返回 `allow / sandbox / approval / block`、审批续跑、Run 和 Tool 去重数，并按 Capability 和 Tool 聚合
3. `GET /api/core/capability-policy/audits/{eventId}` 使用同一 `runCode + toolCallId` 关联策略、审批与 Tool 生命周期事件
4. 查询不返回 Tool 参数、目标资源原文、Tool 输出或审批意见，只展示已持久化的安全 Payload 和摘要哈希
5. `agent_run_stream_events` 增加仅覆盖 `policy.decided` 的 `created_at + run_code + id` 部分索引，不改变 Runtime Event Store 的事实归属
6. 前端“能力策略审计”使用 `tracing` 菜单权限，展示决策分布、Capability/Tool 分布、安全摘要和关联事件时间线

## 10. 验证

1. `RuntimePluginManifestContractTest`：验证插件只能声明九类稳定 Capability
2. `RuntimeCapabilityContractTest`：验证 Tool 能力去空格、未知/重复项拒绝，以及副作用声明一致性
3. `ToolSideEffectServiceTest`：验证顶层 Side Effect 为权威字段，并保留旧快照兼容
4. `CapabilityPolicyEngineTest`：验证安全基线、参数规范化、目标匹配、稳定审批节点和审批授权作用域
5. `ToolRouterCapabilityPolicyTest`：验证阻断与审批均发生在副作用占用和 Provider 调用之前
6. `ExecutionOrchestratorApprovalReplayTest`：验证 Capability 审批只跳过恢复步骤的前置 Guardrail
7. `CapabilityPolicyRuntimeEventServiceTest / RuntimeEventTypeContractTest`：验证决策事件不携带参数和目标原文，并锁定 `policy.decided` 编码
8. `GuardrailPolicyServiceCapabilityTest`：验证 Capability Policy 动作、能力编码和参数条件写入校验
9. `npm run build`：验证 Tool 能力声明与 Capability Policy 动作配置可正常构建
10. `ToolRuntimeEventServiceTest / ApprovalRuntimeEventServiceTest / RuntimeEventQueryServiceTest`：验证审批事件安全关联、审批续跑复用原 Tool Call ID，并统一投影 `itemId`
11. `CapabilityPolicyAuditServiceTest / CapabilityPolicyAuditRepositoryTest`：验证筛选校验、用户隔离、指标聚合和 Tool Call 事件关联
12. `capability-policy-audit.spec.ts`：验证运营页列表、指标、安全哈希与关联事件时间线
13. `run_platform_e2e.sh`：在真实 PostgreSQL/Flyway 环境校验审计分页与聚合 SQL
