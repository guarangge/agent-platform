# Runtime Replay 规范

## 1. 当前结论

M2 将“Prompt 再调用”“录制事实模拟”和“真实步骤重执行”拆成三个明确模式，不能继续用一个模糊的 replay 概念混用：

| Replay Mode | 当前状态 | 是否调用 LLM | 是否调用 Tool/Workflow | 用途 |
| --- | --- | --- | --- | --- |
| `prompt_replay` | 已开放 | 是 | 否 | 使用冻结 Prompt 重新调用指定模型 |
| `simulation_replay` | 已开放（管理员） | 否 | 否，只能注入录制结果 | 复用原 Run 事件和步骤快照验证结果组装 |
| `execution_replay` | 仅预留契约 | 可选 | 可能 | 真实重执行；M2 不开放生产入口 |

Replay 与 Checkpoint Recovery 不同：Replay 产生独立 `replayCode / replayTraceId / result`，不更新原 Task、原 Trace、原 Artifact 和原 Run 状态；Recovery 的目标是继续完成原任务。

## 2. 第一纵向切片

当前已将原 Trace Prompt 回放正式标识为 `prompt_replay`：

```http
POST /api/core/traces/{taskTraceId}/replay
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "modelCode": "MINIMAX_DEFAULT",
  "systemPrompt": "可选覆盖",
  "userPrompt": "可选覆盖"
}
```

兼容保留原响应字段，并新增：

```json
{
  "replayCode": "REPLAY_...",
  "replayMode": "prompt_replay",
  "replayTraceId": "REPLAY_TRACE_...",
  "status": "succeeded",
  "manifest": {}
}
```

安全边界：

1. 当前仅 `ADMIN / SUPER_ADMIN` 可发起正式 Replay
2. Prompt Replay 只调用 LLM Gateway，不进入 Orchestrator、Tool Router、Workflow 或 Sandbox
3. Manifest 固定声明网络写、文件写和业务数据库写均不允许
4. 模型 Manifest 不保存 `apiKey / baseUrl / authConfig / headers`
5. 缺少完整 System/User Prompt、原 Task 或模型配置时明确失败，不静默降级

## 3. Replay Manifest

Manifest 在 Replay 开始前一次性写入，后续状态更新不得覆盖：

1. `manifestVersion / replayCode / replayTraceId / replayMode / capturedAt`
2. 来源 Task、Task Trace、原 Trace、Agent 和执行类型
3. 本次实际使用的安全模型参数快照
4. Prompt 引用和原 Trace Tool 引用
5. Tool Schema 可用性；Prompt Replay 固定为空且 `toolExecutionAllowed=false`
6. 网络、文件、数据库业务写和 Workflow 执行策略摘要
7. 资源快照来源及完整性说明

## 4. Replay Input

Input 与 Manifest 同时冻结，当前 Prompt Replay 包含：

1. 完整 System Prompt 和 User Prompt
2. 两个 Prompt 的 SHA-256
3. 原输出，用于结果 Diff
4. 实际模型编码
5. 原事件范围可用性；Trace Prompt Replay 暂无 Run Event Range，因此显式记录 `available=false`，不能伪造游标

## 5. 持久化

`runtime_replays` 是 Replay 专用审计表：

1. `replay_code` 和 `replay_trace_id` 分别唯一
2. `replay_mode` 固定三种契约值
3. `status` 为 `pending / running / succeeded / failed`
4. `manifest / input_snapshot` 创建后不可更新
5. 完成时只更新 `result_snapshot`，失败时只更新 `error_message`
6. 来源 Task/Trace 通过外键关联，但 Replay 结果不回写来源表

## 6. 后续切片

### 6.1 Simulation Replay

第二个纵向切片已开放管理员 Simulation Replay：

```http
POST /api/core/runtime/replays/runs/{runCode}/simulation
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "fromSequence": 1,
  "toSequence": 120
}
```

`fromSequence / toSequence` 可省略，默认从第一条事件冻结到请求时最后一条已持久化事件。单次最多读取 5000 条，事件序号必须连续。

输入事实源：

1. `agent_run_stream_events`：冻结消息、Run、Tool 生命周期、Approval 和 Artifact 事件
2. `task_steps.output_snapshot`：提供 `step.completed` 对应的 Tool、Workflow 和其他标准步骤真实结果
3. Runtime Event 不保存原始 Tool 正文；Simulation Replay 不会把安全生命周期事件伪装成 Tool 结果
4. 每个完成步骤必须存在 `status=success`、有效 JSON、`resultType` 和 SHA-256，否则 Replay 明确失败

执行边界：

1. `ReplayEngine` 只将 `simulation_replay` 路由到 `SimulationReplayModeHandler`
2. Handler 按事件顺序注入录制步骤结果，并用 `message.reset / message.delta` 在内存中重组回复
3. Handler 不依赖 LLM Gateway、Tool Router、Workflow Executor 或 Sandbox
4. 结果固定记录 Provider、LLM、网络写、文件写和数据库业务写次数为 0
5. Replay 只写自身 `runtime_replays` 审计结果和独立 Replay Trace，不更新来源 Run、Task、Step、Trace 或 Artifact

### 6.2 Structured Event Diff

M2 第三个纵向切片已为 Simulation Replay 增加结构化 `eventDiff`，并写入接口响应及 `runtime_replays.result_snapshot`：

```json
{
  "eventDiff": {
    "diffVersion": 1,
    "consistent": true,
    "summary": {
      "expectedCount": 4,
      "actualCount": 4,
      "matchedCount": 4,
      "changedCount": 0,
      "missingCount": 0,
      "unexpectedCount": 0
    },
    "resultDiff": {
      "status": "matched",
      "originalResult": "...",
      "replayResult": "...",
      "originalSha256": "...",
      "replaySha256": "..."
    },
    "entries": [],
    "inconsistencyReasons": []
  }
}
```

Diff 规则：

1. 步骤结果使用 `step:{taskStepId}` 稳定事实键
2. Tool、Approval、Artifact 事件使用 `event:{sequenceNo}:{eventType}` 稳定事实键
3. 事实内容先递归按 JSON 对象字段名排序，再计算 SHA-256，避免字段顺序产生假差异
4. 状态固定为 `matched / changed / missing / unexpected`
5. `changed` 返回具体顶层变化字段；所有非一致项生成可读的 `inconsistencyReasons`
6. 来源 `message.reset / message.delta` 独立重组为 `originalResult`，与 Replay 的 `assembledMessage` 比较
7. Event ID、发生时间等观测字段不参与事实内容哈希

### 6.3 Artifact 内容与元数据校验

M2 第四个纵向切片新增 `artifactVerification`，并在 Replay Input 中冻结不含正文的 `recordedArtifacts`：

```json
{
  "artifactVerification": {
    "verificationVersion": 1,
    "consistent": true,
    "summary": {
      "artifactCount": 1,
      "artifactEventCount": 2,
      "verifiedCount": 1,
      "mismatchCount": 0
    },
    "entries": [
      {
        "artifactId": "ARTIFACT_...",
        "path": "report.pdf",
        "declaredSize": 1024,
        "actualSize": 1024,
        "mediaType": "application/pdf",
        "contentSha256": "...",
        "status": "verified",
        "sourceEventSequences": [20, 25],
        "issues": []
      }
    ],
    "inconsistencyReasons": []
  }
}
```

校验规则：

1. Sandbox 工作区在执行结束后已经清理，Replay 不读取或恢复工作区文件
2. 内容事实来自冻结的 `task_steps.output_snapshot.payload.artifacts.contentBase64`
3. Base64 解码后的真实字节用于计算 SHA-256 和 `actualSize`
4. 校验快照声明 `size`，以及 `artifact.created / artifact.published` 的 `artifactId / path / fileName / size / mediaType`
5. Artifact ID、文件名和媒体类型复用 Runtime Event 的同一套纯函数，避免 Replay 算法漂移
6. 缺少内容、Base64 损坏、不安全路径或 Event 无对应内容快照时明确失败
7. 大小或事件元数据不一致时 Replay 可以完成，但报告 `metadata_mismatch` 和具体 issue
8. Replay Input 的 `recordedArtifacts` 只保存哈希与文件元数据；正文仍位于已冻结步骤快照内，不再复制一份

### 6.4 图片附件事实校验

Simulation Replay 将 `input.attachment.resolved / input.attachment.analyzed` 作为录制事实处理：

1. 按来源 `sequenceNo + eventType` 建立稳定事实键，并对规范排序后的 Payload 计算 SHA-256
2. 重放只注入附件 ID、MIME、大小、原文件 SHA-256、能力路由、Tool Code 和结果长度，不读取附件存储或调用视觉/OCR Provider
3. Replay Manifest 记录 `recordedAttachmentFactCount` 和 `attachmentIntegritySource`，明确附件正文不属于 Replay Input
4. 引用、SHA-256、路由或分析结果长度发生变化时，Event Diff 报告 `changed / missing / unexpected`，不修改来源 Run 或附件

### 6.5 Replay 管理页面

M2 第五个纵向切片新增管理员 Replay 管理页和只读审计查询接口：

```text
GET  /api/core/runtime/replays
GET  /api/core/runtime/replays/{replayCode}
POST /api/core/runtime/replays/runs/{runCode}/simulation
```

管理边界：

1. 当前仅 `ADMIN / SUPER_ADMIN` 可查询和发起 Replay
2. 列表支持按 `replayMode / status / sourceRunCode` 精确筛选并按创建时间倒序分页
3. 详情返回不可变 `manifest / inputSnapshot` 和 Replay 自身 `resultSnapshot`
4. 页面可查看结构化 `eventDiff`、`artifactVerification` 和失败原因
5. 页面只提供 `simulation_replay` 发起入口；`execution_replay` 仅可用于筛选历史契约，不开放执行按钮
6. 管理查询不修改来源 Run、Task、Trace、Artifact，也不修改 Replay 审计快照

### 6.5 浏览器端到端测试

M2 第六个纵向切片新增 `frontend-web/tests/e2e/replay.spec.ts`，使用固定 HTTP 夹具验证：

1. 管理员可进入 Replay 管理页并加载分页记录
2. 详情可展示不可变 Manifest、Input、Event Diff 和 Artifact 校验
3. `SUPER_ADMIN` 可按明确事件范围发起 `simulation_replay`
4. 页面提交的 `fromSequence / toSequence` 与后端请求一致
5. 普通用户访问 `/replays` 时在前端路由层阻断，且不会调用 Replay API

测试不依赖真实 LLM、Tool Provider、Sandbox 或外部数据库，避免把第三方可用性误判为 Replay 回归。

### 6.6 Feature Flag 与 M2 最终审计

M2 第七个纵向切片在后端执行入口增加总开关、模式开关、Project/Agent 白名单和稳定百分比灰度。未命中策略时在读取 Event/Step Snapshot 和创建 Replay 实例前失败；管理查询不受执行开关影响。

成功 Replay 的 Manifest 新增：

```json
{
  "featureDecision": {
    "enabled": true,
    "replayMode": "simulation_replay",
    "reason": "percentage_rollout",
    "rolloutBucket": 12
  }
}
```

最终审计结论：

1. `prompt_replay` 仅允许 LLM 推理请求，禁止 Tool、Workflow、文件写、业务网络写和业务数据库写
2. `simulation_replay` 不注入 LLM/Tool/Workflow/Sandbox 依赖，只消费冻结 Event 和步骤快照
3. Replay 只写 `runtime_replays` 和独立 Replay Trace，不回写来源 Run、Task、Step、Trace 或 Artifact
4. Manifest 和 Input 只在实例创建时写入；完成/失败只更新 Result、状态或错误信息
5. `execution_replay` 固定返回未开放，Feature Flag 不能绕过
6. 管理查询和执行均要求 `ADMIN / SUPER_ADMIN`，前端和后端双重校验
7. Artifact 正文不进入 Runtime Event，Replay Input 只额外保存哈希和安全元数据
8. 关闭 Feature Flag 不删除历史审计，数据库无需回滚

灰度顺序、环境变量和回滚命令见 `docs/runtime/Replay发布与回滚.md`。

至此 M2 可验证 Replay 计划全部完成。
