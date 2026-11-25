# Runtime Protocol 规范

## 1. 当前结论

Runtime Protocol 用于统一表达 Agent Run 的持久化运行事件。当前版本已覆盖流式消息、安全执行进度以及 Run、Plan、Step、Tool、Approval、Artifact、Checkpoint 生命周期，不替换现有 Conversation、Task、Step、Trace、Approval 等业务查询模型。

当前实现状态：V1 M1 已完成

当前事件事实源：`agent_run_stream_events`

当前统一订阅接口：

```http
GET /api/core/runtime/runs/{runCode}/events
Accept: text/event-stream
Authorization: Bearer <token>
Last-Event-ID: 12
```

也可以使用查询参数指定游标：

```http
GET /api/core/runtime/runs/{runCode}/events?afterSequence=12
```

如果同时传入 `Last-Event-ID` 和 `afterSequence`，服务端使用两者中的较大值。
两个游标都必须是非负整数，非法值返回业务错误。

---

## 2. Runtime Event Envelope

```json
{
  "eventId": "EVT_21",
  "sequenceNo": 13,
  "threadId": 8,
  "turnId": null,
  "runId": "RUN_ABC",
  "taskId": 11,
  "itemId": null,
  "eventType": "run.progress",
  "occurredAt": "2026-08-31T10:00:00",
  "payloadVersion": 1,
  "payload": {
    "message": "正在生成执行计划"
  },
  "metadata": {
    "stage": "planning"
  }
}
```

字段规则：

1. `eventId` 当前由持久化事件主键生成，格式为 `EVT_{id}`
2. `sequenceNo` 在同一个 Run 内单调递增，用于排序和断线续传
3. `threadId` 当前映射为 `conversation_id`
4. `turnId` 在 Turn 独立持久化前允许为空；Plan、Step、Tool、Approval、Artifact、Checkpoint 生命周期事件分别使用稳定业务标识映射 `itemId`
5. `runId` 当前映射为 `run_code`
6. `payloadVersion` 用于约束对应事件 Payload Schema
7. `payload` 只保存事件类型直接载荷
8. `metadata` 保存安全执行阶段、来源和扩展属性，不保存模型思维链

---

## 3. V1 首批事件类型

| 标准事件类型 | Payload | Metadata | 历史兼容事件 |
| --- | --- | --- | --- |
| `message.reset` | `{}` | 可选 | `reset` |
| `message.delta` | `{ "content": "..." }` | 可选 | `delta` |
| `run.progress` | `{ "message": "..." }` | `stage` 等安全阶段 | `progress` |
| `run.queued` | `status / priority / retry / backoffSeconds` | 可选 | 无 |
| `run.started` | `status / attemptCount / maxAttempts` | `workerId` | 无 |
| `run.mode.selected` | `taskId / requestedMode / effectiveMode / allowedModes / reason / fallback` | `traceId` | 无 |
| `run.loop.stopped` | `taskId / stopReason / iterations / llmCalls / toolCalls / tokens / costAmount / uniqueToolFingerprints` 及对应预算上限 | `traceId` | 无 |
| `run.waiting` | `status / taskId` | 可选 | 无 |
| `run.completed` | `status / taskId` | 可选 | 无 |
| `run.failed` | `status / taskId / errorMessage` | 可选 | 无 |
| `run.cancelled` | `status / taskId` | 可选 | 无 |
| `step.started` | `taskStepId / stepId / stepNo / stepCount / stepType / stepName / status` | 可选 | 无 |
| `step.completed` | Step 基础字段及 `skipped` | 可选 | 无 |
| `step.failed` | Step 基础字段及 `errorMessage / failureType` | 可选 | 无 |
| `step.waiting` | Step 基础字段，`status` 区分审批、事件等待和人工暂停 | 可选 | 无 |
| `tool.call.requested` | `toolCallId / toolCode / providerType / transportType / taskId / status / inputLength / argumentNames` | `traceId` | 无 |
| `tool.call.started` | Tool 基础字段及 `status=running` | `traceId` | 无 |
| `tool.call.completed` | Tool 基础字段及 `durationMs / responseLength / outputSnapshotLength` | `traceId` | 无 |
| `tool.call.failed` | Tool 基础字段及 `durationMs / errorCode / errorType / retryable / providerStatusCode` | `traceId` | 无 |
| `policy.decided` | `toolCallId / toolCode / decision / requestedAction / policyCode / policyVersion / runtimeCapabilities / sideEffectLevel / parameterHash / targetHash / approvalNodeId / approvalGranted / taskId` | `traceId` | 无 |
| `approval.requested` | `approvalId / taskId / taskStepId / nodeId / nodeName / approvalSourceType / status=pending`；Capability 审批附加 `toolCallId / parameterHash` | `traceId` | 无 |
| `approval.resolved` | Approval 基础字段及 `status=approved/rejected/timed_out / resolvedBy`；Capability 审批附加 `toolCallId / parameterHash` | `traceId` | 无 |
| `approval.revoked` | Approval 基础字段及 `status=revoked / resolvedBy` | `traceId` | 无 |
| `artifact.created` | `artifactId / taskId / taskStepId / path / fileName / size / mediaType / status=created` | 可选 | 无 |
| `artifact.published` | Artifact 基础字段及 `status=published / downloadable / downloadUrl` | 可选 | 无 |
| `input.attachment.resolved` | `route / modelCode / attachments[]`，附件项仅含 ID、编码、MIME、大小和 SHA-256 | `traceId` | 无 |
| `input.attachment.analyzed` | `attachmentId / attachmentCode / mimeType / sha256 / toolCode / responseLength` | `traceId` | 无 |
| `checkpoint.created` | `checkpointId / taskId / taskStepId / checkpointStatus / currentStepNo / nextStepNo / planId / status=created` | `traceId` | 无 |
| `checkpoint.restored` | Checkpoint 基础字段及 `status=restored / recoveryMode / recoveryDisposition` | `traceId` | 无 |
| `plan.created` | `taskPlanId / planId / taskId / planVersion / planType / planStatus / stepCount / source / qualityScore / status=created` | `traceId` | 无 |
| `plan.replanned` | Plan 基础字段及 `previousPlanId / previousPlanVersion / changeSummary / status=replanned` | `traceId` | 无 |

写入规则：

1. 新事件必须使用标准事件类型
2. 历史 `reset / delta / progress` 读取时自动映射到标准类型
3. 旧 SSE 接口同时识别历史类型和标准类型
4. 未被当前枚举识别、但符合数据库格式约束的未来事件仍可通过统一接口原样读取
5. Tool 事件只记录参数名、长度、错误分类和结果长度，不记录参数值、响应正文或输出快照正文
6. `run.mode.selected` 记录业务执行模式的确定性选择；当前 `interactive_loop` 未开放时必须明确记录回退原因
7. `run.loop.stopped` 只记录安全计数和稳定停止原因，不记录 Tool 参数、参数哈希、调用指纹、Prompt 或模型思维链
6. `requested / started` 事件写入失败时阻止尚未发生的 Provider 调用；Provider 已返回后的结果事件写入失败只记录错误，不能因此重复执行外部副作用
7. Approval 事件不保存审批意见和完整恢复元数据；Capability 审批仅允许投影格式合法的 `toolCallId / parameterHash`；对话窗口收到控制事件后查询审批详情并展示弹窗，不将控制状态追加为聊天消息
8. 审批通过后恢复原 Run 作用域，续跑产生的 Step / Tool 事件继续写入原 Run 事件流
9. Artifact 事件以步骤输出快照 `payload.artifacts` 为事实源；`artifactId` 由 `taskId + path` 确定性生成，created / published 使用同一标识
10. Artifact 事件只保存路径、文件名、大小、媒体类型和受保护下载地址，不保存 `contentBase64` 文件正文；published 与最终回复使用同一个下载地址构建逻辑
11. `checkpoint.created` 与 `task_execution_checkpoints` 快照插入处于同一事务，事件不复制 Plan、Decision 和 Step Results 快照正文
12. `checkpoint.restored` 只在 `task_resume / approval_resume` 实际加载持久化快照时发布；自动恢复准备、可恢复性查询和副作用歧义检查不提前发布恢复事件
13. Task 的首个计划版本发布 `plan.created`，后续 `auto_replan / observation_replan / manual_adjust / reject_replan / resume` 等新版本统一发布 `plan.replanned`
14. Plan 事件与 `task_plans / task_plan_steps` 版本快照处于同一事务，只保存版本定位、来源、质量评分和步骤增删改计数，不复制目标、步骤、假设、约束和风险正文
15. `policy.decided` 必须在副作用占用和 Provider 调用前写入，只保存参数及目标哈希，不保存 Tool 参数值和目标资源原文；写入失败时禁止继续调用 Provider
16. Capability 审批仅将格式合法的 `toolCallId / parameterHash` 投影到 Approval 事件；审批通过后节点和参数哈希均一致时，后续 `tool.call.*` 复用原 Tool Call ID
17. `policy.decided` 与 `tool.call.*` 使用 `toolCallId` 作为统一查询 Envelope 的 `itemId`，Approval 事件继续使用 `APPROVAL_{approvalId}`
18. Capability Policy 全量审计复用 `policy.decided` 事件；运营查询必须同时校验 `agent_runs.user_id / tenant_id / organization_id / workspace_id / project_id`，详情只关联同一 `runCode + toolCallId` 的策略、审批和 Tool 安全事件
19. 图片输入在 Provider 调用前发布 `input.attachment.resolved`；OCR 完成后尽力发布 `input.attachment.analyzed`，后置事件失败不得触发 Provider 重试
20. 两类附件事件禁止保存 `storageKey`、图片 Base64、OCR 正文和 Provider 原始响应；附件身份与完整性只通过 `attachmentId + sha256` 冻结

Capability Policy 运营查询：

| 接口 | 用途 |
| --- | --- |
| `GET /api/core/capability-policy/audits` | 分页和多维筛选全量决策 |
| `GET /api/core/capability-policy/audits/metrics` | 查询动作指标及 Capability / Tool 聚合 |
| `GET /api/core/capability-policy/audits/{eventId}` | 查询单次决策及同 Tool Call 关联事件链 |

---

## 4. SSE 传输格式

每条 Runtime Event 使用统一 SSE 事件名：

```text
id: 13
event: runtime.event
data: {RuntimeEventEnvelope JSON}
```

客户端处理规则：

1. 按 `sequenceNo` 处理事件，不按到达时间排序
2. 成功处理后保存最后一个 `sequenceNo`
3. 断线重连时通过 `Last-Event-ID` 或 `afterSequence` 继续读取
4. 重复收到已处理序号时必须幂等忽略
5. 当前 Run 进入终态、待审批、持久等待或人工暂停后，服务端结束本次 SSE 连接
6. 连接结束不等同于任务永久完成，`waiting_approval / waiting_event / paused` 后续恢复时应重新建立连接

权限规则：

1. 接口需要 `ROLE_ADMIN` 或 `SCOPE_chat`
2. 服务端通过 `AgentRunService` 校验 Run 是否属于当前用户
3. 不允许仅凭 `runCode` 跨用户订阅

---

## 5. 旧接口兼容

旧接口继续保留：

```http
GET /api/core/conversations/{conversationId}/runs/{runCode}/stream
```

兼容规则：

1. `message.reset` 投影为旧 `reset`
2. `message.delta` 投影为旧 `delta`
3. `run.progress` 投影为旧 `progress`
4. 最终落库消息继续通过旧 `replace` 校准
5. 旧接口继续作为兼容与灰度回滚路径，完成兼容观察前不得删除

---

## 6. 当前未包含范围

M1 计划内的后端基础生命周期事件、会话页统一消费器、协议兼容测试、Agent 灰度开关和旧 SSE 回滚路径已经接入，并完成本地 Docker 全量发布验证。

灰度开关、发布顺序和回滚命令见 `docs/runtime/Runtime-Protocol发布与回滚.md`。

Run、Task、Step、Approval、Durable Wait 和 Checkpoint 的状态边界见 `docs/runtime/Runtime状态机.md`。

首个版本也暂不包含：

1. 独立 Turn / Item 表
2. `simulation_replay`
3. Event Projection Outbox
4. 旧 SSE 下线
