# AgentLoopKernel 发布与回滚

## 1. 发布原则

M3 第一个切片的 Adapter 和旧直接路径最终都调用同一个 `ExecutionOrchestratorService`。灰度的目标是验证路由、依赖装配和调用契约，不进行影子双执行，避免 Tool、文件、网络和数据库副作用重复发生。

## 2. 灰度配置

macOS / Linux，在 `agent-core` 部署环境配置后滚动重启：

```bash
# 指定 Agent 灰度
export CORE_AGENT_LOOP_KERNEL_ENABLED=true
export CORE_AGENT_LOOP_KERNEL_PROJECT_IDS=20
export CORE_AGENT_LOOP_KERNEL_AGENT_IDS=10,12
export CORE_AGENT_LOOP_KERNEL_ROLLOUT_PERCENT=0

# 取消白名单，进行 20% 稳定灰度
export CORE_AGENT_LOOP_KERNEL_AGENT_IDS=
export CORE_AGENT_LOOP_KERNEL_PROJECT_IDS=20,21
export CORE_AGENT_LOOP_KERNEL_ROLLOUT_PERCENT=20

# 全量通过 Adapter
export CORE_AGENT_LOOP_KERNEL_ROLLOUT_PERCENT=100
```

Windows PowerShell：

```powershell
$env:CORE_AGENT_LOOP_KERNEL_ENABLED = "true"
$env:CORE_AGENT_LOOP_KERNEL_PROJECT_IDS = "20"
$env:CORE_AGENT_LOOP_KERNEL_AGENT_IDS = "10,12"
$env:CORE_AGENT_LOOP_KERNEL_ROLLOUT_PERCENT = "0"
```

## 3. 回滚

```bash
CORE_AGENT_LOOP_KERNEL_ENABLED=false
```

PowerShell：

```powershell
$env:CORE_AGENT_LOOP_KERNEL_ENABLED = "false"
```

重启 `agent-core` 后立即恢复 `ConversationService -> ExecutionOrchestratorService` 旧直接路径。回滚不涉及数据库 migration，不修改运行中的 Task、Checkpoint、Replay 或 Artifact。

## 4. 每批检查

1. 检查 `AgentLoop 执行路由` 日志中的 `projectId / agentId / kernelEnabled / reason / rolloutBucket`
2. 检查同一 Task 只产生一组 Step 和 Tool 调用
3. 检查审批、恢复、Durable Wait 和 Artifact 链路与旧路径一致
4. 检查异常类型和用户最终响应没有变化
5. 出现重复执行、步骤数量变化或恢复失败时立即关闭总开关
6. 检查 Workflow 恢复仍从原节点继续，Approval 仍进入原挂起状态，Tool 失败分类和重试次数不变
7. 检查 `step_decision` Trace 中的 `action / reason / metadata` 与旧版一致，`replan` 仍从原触发步骤重新规划
8. 检查 `step_started / step_succeeded / step_failed / waiting_approval / waiting_event / paused` Checkpoint 写入次数和序号不变
9. 检查 Worker 自动恢复、审批恢复、副作用歧义处置和 Checkpoint 分支入口均可用
10. 检查 `run.progress / step.started / step.completed / step.failed / step.waiting / approval.requested` 数量和 payload 不变
11. 检查 Step 状态与其 Runtime Event 仍同时成功或同时回滚，Artifact Event 没有重复
12. 检查 `approval.requested / approval.resolved / approval.revoked` 与 Approval 状态仍同时成功或同时回滚
13. 检查审批通过后弹窗关闭、聊天区不出现审批控制文案，续跑产生的 Step / Tool 事件仍属于原 Run
14. 检查已有 terminal reply 不触发二次 LLM 汇总，单步骤和多步骤最终回复与旧路径一致
15. 检查 Artifact 矛盾文案仍被校正，下载链接与 `artifact.published` 事件不重复且内容一致
16. 检查响应组装异常后最终回复流式 Scope 已关闭，后续非最终步骤不会错误外发模型 delta
17. 检查升级前保存的 `step_results_snapshot` 可恢复为 `RuntimeStepResultContext`，字段和执行顺序不变
18. 检查 `RuntimeExecutionAggregate` 的审批、外部事件和人工暂停判定与升级前一致，Gateway 两条路径仍返回同一结果实例
19. 检查稳定单步执行模型升级后，Workflow 恢复参数、Approval 挂起异常及普通 Handler 请求和结果仍原样传递
20. 检查升级前后的 Checkpoint 查询、自动恢复、歧义处置和分支接口 JSON 字段一致，恢复步骤和分支 Trace 不变
