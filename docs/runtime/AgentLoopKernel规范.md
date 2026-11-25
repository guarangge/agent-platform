# AgentLoopKernel 规范

## 1. 当前阶段

M3 十二个纵向切片已建立可替换执行内核、Runtime Context、单步执行、Observation 决策、Checkpoint 存储、Runtime Event 出口、审批协调、最终响应收口及独立执行模型，不复制现有业务逻辑：

```text
ConversationService
  -> AgentLoopExecutionGateway
     -> RuntimeContextProvider
     -> legacy direct: ExecutionOrchestratorService.executePlan
     -> kernel adapter: LegacyAgentLoopKernelAdapter
                        -> ExecutionOrchestratorService.executePlan
                           -> RuntimeStepExecutor
                              -> LegacyRuntimeStepExecutorAdapter
                                 -> WorkflowExecutorService / ApprovalStepService / ExecutionStepHandlerRegistry
                           -> ObservationPolicy
                              -> LegacyObservationPolicyAdapter
                                 -> ObservationDecisionService
                           -> CheckpointStore
                              -> LegacyCheckpointStoreAdapter
                                 -> ExecutionCheckpointService / PostgreSQL
                           -> RuntimeEventSink
                              -> LegacyRuntimeEventSinkAdapter
                                 -> AgentRunStreamEventService
                                 -> TaskStepLifecycleEventService
                           -> ApprovalCoordinator
                              -> LegacyApprovalCoordinatorAdapter
                                 -> ApprovalRuntimeEventService
                           -> RuntimeResponseAssembler
                              -> LegacyRuntimeResponseAssemblerAdapter
                                 -> ExecutionResponseAssemblerService
                                 -> ArtifactResponseConsistencyService
```

默认走 `legacy direct`。灰度进入 `kernel adapter` 时，`RuntimeExecutionRequest` 和返回的 `RuntimeExecutionAggregate` 保持同一实例语义，两条路径仍只执行一次相同的 `ExecutionOrchestratorService`；Tool、Workflow、审批、Checkpoint、Artifact、Runtime Event 和响应组装通过 Kernel 契约复用现有实现，不复制业务逻辑。

## 2. 首批契约

1. `AgentLoopKernel`：可替换执行循环入口，返回稳定 `kernelCode`
2. `AgentLoopExecutionRequest`：组合稳定 `RuntimeExecutionRequest` 与已解析 Runtime Context
3. `AgentLoopExecutionResult`：包装稳定 `RuntimeExecutionAggregate`
4. `ExecutionModeStrategy`：决定旧直接调用或 Kernel Adapter
5. `AgentLoopExecutionGateway`：会话主链唯一过渡路由
6. `LegacyAgentLoopKernelAdapter`：复用旧 Orchestrator 的首个 Adapter
7. `RuntimeContextProvider`：从冻结的 `ExecutionContext` 解析内核需要的作用域和资源标识
8. `RuntimeStepExecutor`：可替换的单步执行入口
9. `RuntimeStepExecutionRequest`：组合稳定单步执行命令与 Workflow 恢复上下文
10. `LegacyRuntimeStepExecutorAdapter`：统一适配 Workflow、Approval 和现有 Step Handler Registry
11. `ObservationPolicy`：可替换的步骤后 `continue / replan / finish` 决策入口
12. `ObservationPolicyRequest`：包含当前 Plan、Step、已完成结果和标准输出快照
13. `LegacyObservationPolicyAdapter`：原样委托现有规则 / LLM observation 决策服务
14. `CheckpointStore`：可替换的 Checkpoint 保存、读取与恢复契约
15. `CheckpointSaveRequest`：封装执行请求、步骤边界、状态和已完成结果
16. `LegacyCheckpointStoreAdapter`：原样委托现有 PostgreSQL Checkpoint 服务
17. `RuntimeEventSink`：统一运行进度和 Step 生命周期的核心事件出口
18. `LegacyRuntimeEventSinkAdapter`：将 Kernel 事件命令原样映射到现有事务 Service
19. `ApprovalCoordinator`：统一 Approval 创建、状态迁移和审批续跑 Run Scope 的协调契约
20. `LegacyApprovalCoordinatorAdapter`：将审批命令和作用域原样委托给现有 Approval Runtime Event 服务
21. `RuntimeResponseAssembler`：统一最终响应汇总、流式作用域和 Artifact 一致性收口契约
22. `LegacyRuntimeResponseAssemblerAdapter`：将最终响应请求原样委托给现有响应汇总和 Artifact 一致性服务
23. `RuntimeStepResultContext`：跨 Kernel、执行、恢复和响应链共享的独立步骤结果模型
24. `RuntimeExecutionRequest`：Agent Loop、Orchestrator、Checkpoint 和会话主链共享的稳定执行请求
25. `RuntimeExecutionAggregate`：包含最终内容、实际计划和挂起状态的稳定执行结果
26. `RuntimeStepExecutionCommand`：Plan、Workflow 与 Step Handler 共享的稳定单步执行命令
27. `RuntimeStepExecutionOutcome`：Kernel、Workflow 与 Step Handler 共享的稳定单步执行结果
28. `RuntimeCheckpoint`：Checkpoint Store、持久化实现和恢复主链共享的稳定快照模型
29. `RuntimeCheckpointForkResult`：Checkpoint 分支创建的稳定结果模型

第九个切片已将原 `StepResultContext` 提升为独立 `RuntimeStepResultContext`；第十个切片进一步移除 Orchestrator 嵌套的执行请求、恢复快照和聚合结果；第十一个切片将单步执行命令与结果迁入稳定 Runtime model；第十二个切片将 Checkpoint 与分支结果迁入稳定 Runtime model。Kernel 契约已不再引用具体 Orchestrator 或 Checkpoint Service 嵌套类型。

## 3. Feature Flag

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CORE_AGENT_LOOP_KERNEL_ENABLED` | `false` | AgentLoopKernel Adapter 总开关 |
| `CORE_AGENT_LOOP_KERNEL_PROJECT_IDS` | 空 | Project ID 白名单，英文逗号分隔；配置后先校验 Project |
| `CORE_AGENT_LOOP_KERNEL_AGENT_IDS` | 空 | Agent ID 白名单，英文逗号分隔；配置后白名单优先 |
| `CORE_AGENT_LOOP_KERNEL_ROLLOUT_PERCENT` | `0` | Agent 白名单为空时按 Project ID + Agent ID 做 SHA-256 稳定百分比灰度 |

`ExecutionContext.CurrentUserContext` 现已冻结 `tenantId / organizationId / workspaceId / projectId`，`RuntimeContextProvider` 不依赖 ThreadLocal 解析灰度作用域，同一执行快照可以稳定重放路由决策。

## 4. 不变量

1. Feature Flag 关闭时直接调用旧 Orchestrator
2. Adapter 不得复制执行循环、重放步骤或并行执行新旧内核
3. Adapter 不改变输入快照、恢复快照、步骤编号、返回结构和异常类型
4. 新增 Step Handler 继续通过 `ExecutionStepHandlerRegistry` 扩展
5. 当前切片不修改数据库、不新增 Runtime Event、不改变审批和产物语义
6. 路由日志只记录 Task、Agent、决策原因和分桶，不记录 Token、Prompt 或 Tool 参数
7. Orchestrator 不再直接依赖 `WorkflowExecutorService / ApprovalStepService / ExecutionStepHandlerRegistry`
8. `RuntimeStepExecutor` 不捕获或转换 Approval、Tool、Workflow 异常，原异常继续交由内核循环处理
9. `ObservationPolicy` 只返回决策，不写 Trace、Checkpoint，不直接抛出重规划异常
10. Orchestrator 不再直接依赖 `ObservationDecisionService`，但仍负责解释 `finish / replan / continue`
11. Orchestrator 和 Conversation 恢复主链不再直接依赖 `ExecutionCheckpointService`
12. Checkpoint 保存状态、步骤序号、Plan / Decision / Result JSON 和 Runtime Event 写入时机不变
13. `RuntimeCheckpoint / RuntimeCheckpointForkResult` 字段及 JSON 结构与原 Checkpoint Service 嵌套类型保持一致
14. 进度投影继续是 best-effort，写入失败不中断主任务
15. Step 和 Approval 仍在原 Service 事务中完成状态写入与 Runtime Event 发布，两个 Adapter 均不拆分事务
16. Step completed 仍由 `TaskStepLifecycleEventService` 联动 Artifact Event，不重复发布
17. Checkpoint Event 继续属于 `CheckpointStore` 内部事务
18. Orchestrator、Conversation 和审批 HTTP 入口不再直接依赖 `ApprovalRuntimeEventService`
19. `ApprovalCoordinator` 不解释审批业务规则，只协调请求、状态事件和原 Run 作用域
20. 审批通过、拒绝、超时、撤回的状态与 Runtime Event 原子性不变，审批意见仍不进入 Runtime Event 或聊天消息
21. 审批续跑关闭 Scope 后必须恢复调用线程现场，后续 Step / Tool 事件仍关联原 Run
22. Orchestrator 不再直接依赖 `ExecutionResponseAssemblerService / ArtifactResponseConsistencyService`
23. 最终回复仍依次遵循 terminal reply、单响应片段、LLM 汇总的原优先级，不重复生成已完成 reply
24. Artifact 仍以结构化步骤快照为事实源，矛盾回复校正、下载链接和 published 事件时机不变
25. 最终响应汇总和 Artifact 收口共享同一流式 Scope，成功或异常结束后都必须恢复调用线程现场
26. Step 内的 reply / reflection 生成仍属于 `RuntimeStepExecutor` 实现，不并入最终响应组装契约
27. `RuntimeStepResultContext` 字段名和 JSON 结构保持不变，历史 Checkpoint 无需迁移即可恢复
28. Observation、Checkpoint、Response 契约不得重新引用 `ExecutionOrchestratorService.StepResultContext`
29. `RuntimeExecutionRequest` 的执行上下文、计划、授权、步骤偏移及恢复快照保持原字段顺序和对象语义
30. `RuntimeExecutionAggregate` 的 `waitingApproval / waitingEvent / paused` 判定与原聚合结果完全一致
31. 除 Gateway 旧直连分支和 `LegacyAgentLoopKernelAdapter` 外，Kernel 契约不得依赖具体 Orchestrator
32. `RuntimeStepExecutionCommand` 保持执行上下文、Plan、Step、前序结果、授权和操作人字段的原对象语义
33. Workflow 恢复参数继续由 `RuntimeStepExecutionRequest` 独立携带，Approval 挂起异常和普通 Handler 结果不得被 Adapter 转换
34. Checkpoint 查询、自动恢复、恢复标记、歧义处置和分支创建均直接传递稳定模型，不在 Adapter 中复制对象
35. `RuntimeCheckpoint` 的计划、决策、步骤结果和恢复处置字段保持原对象与 JSON 语义，HTTP 返回结构不变

## 5. 验证

1. `AgentLoopExecutionGatewayTest`：验证关闭走旧直接路径、开启走 Adapter，且不会双执行
2. `LegacyAgentLoopKernelAdapterTest`：验证原请求原样委托、原聚合结果原样返回
3. `DefaultExecutionModeStrategyTest`：验证默认关闭、Agent 白名单和稳定百分比分桶
4. `AgentLoopKernelPropertiesTest`：验证环境变量映射配置可绑定
5. `DefaultRuntimeContextProviderTest`：验证作用域和资源标识只从冻结 `ExecutionContext` 解析
6. `LegacyRuntimeStepExecutorAdapterTest`：验证 Workflow 恢复参数和快照、Approval 挂起异常、普通 Handler 请求与结果均原样传递
7. `LegacyObservationPolicyAdapterTest`：验证原 observation 输入和 `action / reason / metadata` 决策契约原样传递
8. `LegacyCheckpointStoreAdapterTest`：验证保存快照入参以及查询、自动恢复、恢复标记、歧义处置和分支结果均原样委托
9. `LegacyRuntimeEventSinkAdapterTest`：验证进度、Step 创建/迁移命令字段、返回 ID 和异常均原样委托
10. `LegacyApprovalCoordinatorAdapterTest`：验证 Approval 请求/迁移字段、异常和原 Run Scope 均原样委托
11. `LegacyRuntimeResponseAssemblerAdapterTest`：验证响应请求对象、Artifact 收口顺序、最终回复 Scope 和异常均原样传递
12. `RuntimeStepResultContextTest`：验证独立步骤结果模型可无损读取并写回旧 Checkpoint JSON
13. `RuntimeExecutionContractTest`：验证执行上下文、恢复快照保持同一对象，以及审批、外部事件和人工暂停状态语义不变
14. `RuntimeStepExecutionContractTest`：验证稳定单步命令的全字段对象语义和执行结果字段契约
15. `RuntimeCheckpointContractTest`：验证 Checkpoint 快照对象语义及 Checkpoint、ForkResult 历史 JSON 字段无损往返

后续每迁出一个职责都必须增加独立 Contract Test。

## 6. M3 最终审计清单

已完成：

1. Kernel 计划内的七个可替换职责接口及 Legacy Adapter 已全部落地
2. `RuntimeStepResultContext` 已脱离 Orchestrator 嵌套类型
3. `RuntimeExecutionRequest / RuntimeExecutionAggregate` 已替代 Orchestrator 嵌套请求与结果
4. `AgentLoopExecutionRequest / Result` 已不再反向依赖具体 Orchestrator 类型
5. 核心事件写入经 `RuntimeEventPublisher`，PostgreSQL 由 `PersistentRuntimeEventPublisher` 实现
6. 统一事件读取由 `RuntimeEventQueryService` 投影，`AgentRunStreamEventService` 仅保留模型流发布和旧 SSE 查询兼容门面
7. `RuntimeStepExecutionCommand / RuntimeStepExecutionOutcome` 已替代步骤 Service 层旧执行 DTO
8. `RuntimeStepExecutionRequest / RuntimeStepExecutor` 已只依赖稳定 Runtime model
9. `RuntimeCheckpoint / RuntimeCheckpointForkResult` 已替代 Checkpoint Service 嵌套返回类型
10. `CheckpointStore`、Conversation 恢复链和 HTTP 返回已统一依赖稳定 Runtime model

M3 最终审计已完成，不再存在 Kernel 契约反向依赖具体 Orchestrator 或 Checkpoint Service 返回类型的过渡耦合。
