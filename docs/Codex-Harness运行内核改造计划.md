# Codex Harness 启发的运行内核改造计划

## 1. 结论

本次改造不引入新的微服务，不使用 Codex harness 替换现有平台，也不把平台改造成单一代码 Agent。

总体方向如下：

1. 保留 `registry-center` 作为 Agent、Tool、Workflow、Prompt、模型、权限和版本的控制面
2. 保留 `agent-core` 作为会话、Run、Task、执行、审批、恢复、产物和审计的数据面
3. 在 `agent-core` 内建立统一 `Runtime Protocol`，将消息、进度、Tool、审批、产物、错误和终态统一为可持久化事件
4. 先完成事件事实源和无副作用 Replay，再拆分执行内核，避免先重构后补协议
5. 将 Sandbox 和审批从 Planner 显式判断逐步下沉为 Tool Capability Policy
6. 保留企业确定性 Workflow，同时增加受限的 `interactive_loop` 执行模式
7. 将当前 Tool Jar 插件扩展为覆盖 Tool、Step Handler、Context Provider、Policy、Hook 和 Skill 的统一插件规范

参考基线：

1. OpenAI 官方对 Codex harness 的定位是管理 conversation state、流式执行、Tool、Sandbox、审批和跨 Turn 延续，并通过 SDK / app-server 暴露 Thread、Turn、Event 和 Approval 生命周期
2. 官方说明：https://developers.openai.com/blog/codex-as-a-platform

计划状态：M1、M2、M3 已完成；M4 Runtime Plugin 和 Capability Policy 当前范围已完成；M5 双执行模式与 Runtime SDK 已完成；M6 多模态输入与图片能力已完成

计划日期：2026-08-31

---

## 2. 当前基础与主要差距

### 2.1 已具备基础

当前平台已经具备以下可复用能力：

1. `Conversation -> Run -> Task -> Plan -> Step` 持久化执行链
2. `ExecutionOrchestratorService` 计划编排、Observation、Replan、审批挂起和结果组装
3. `ExecutionStepHandlerRegistry` 标准步骤处理器注册机制
4. Internal / External HTTP / MCP / Sandbox Tool Provider
5. Tool Schema、Hook、熔断、Fallback、成本、幂等和补偿
6. Docker 强隔离 Sandbox
7. 持久化 Checkpoint、自动恢复、人工副作用核验和 Checkpoint Fork
8. SSE 增量回复、执行进度和断线续传
9. Context Compaction、Memory、Prompt 快照和 Trace
10. Eval、发布门禁、租户隔离、RBAC 和运营治理
11. Maven 领域 Tool 插件和 `PropertiesLauncher` 外部加载

### 2.2 主要差距

1. Runtime API 分散在会话、Run、Task、Trace、审批和恢复接口中，没有统一生命周期协议
2. SSE 事件主要为 `progress / reset / delta`，无法完整表达 Tool、审批、产物、错误和终态
3. 当前 Trace Replay 只重新调用 LLM，不是完整执行过程重放
4. Checkpoint 解决的是故障恢复，不等同于基于不可变事件的 Replay
5. `ExecutionOrchestratorService` 仍同时承担较多编排、持久化、错误、审批和快照拼装职责
6. 插件化主要覆盖本地 Tool Executor，尚未形成完整插件 Manifest、权限和生命周期契约
7. Sandbox 主要作为显式 Tool，尚未成为所有危险能力执行前的统一策略边界
8. 当前执行以预生成 Plan 为主，探索型 Tool 任务缺少受限的模型原生交互循环
9. 多模型主要通过 Prompt 重建上下文，没有统一的 Provider 原生会话状态适配层

---

## 3. 改造原则

### 3.1 兼容性原则

1. 不修改现有公共接口返回结构，新增能力优先使用 `/v2` 路径或可选字段
2. 旧 SSE 接口在迁移期继续可用，由新版事件自动投影为旧 `progress / reset / delta`
3. 现有 Task、Task Step、Trace、Approval、Artifact 数据继续作为业务查询模型
4. 新事件存储作为运行事实源，不要求一次性替换全部现有表
5. 所有新能力通过 Agent / Project 级 Feature Flag 灰度启用
6. 现有 Workflow、Recovery、审批和 Tool 幂等逻辑不得因内核拆分退化

### 3.2 双服务边界

`registry-center` 负责：

1. Plugin、Skill、Tool Capability 和 Runtime Policy 定义
2. 资源版本、发布、依赖、权限和灰度配置
3. Agent 默认执行模式及可用能力配置

`agent-core` 负责：

1. Thread、Turn、Run、Item 和 Event 的运行实例
2. Agent Loop、Plan、Step、Tool、Sandbox 和审批执行
3. Checkpoint、Replay、恢复、Trace、产物和运行状态
4. Runtime Plugin 的装配与运行时隔离

### 3.3 安全原则

1. LLM 可以提出 Tool 调用，但不能自行决定风险等级或绕过审批
2. Tool 风险根据 Capability、参数、作用域、副作用和策略确定
3. Replay 默认不得重复执行外部副作用
4. 插件默认最小权限，必须显式声明网络、文件、进程、数据库和外部写入能力
5. 任何执行重放都必须关联原 Run、原事件区间和 Replay Mode

---

## 4. 目标运行模型

### 4.1 核心对象映射

| 目标概念 | 当前对象 | 改造方式 |
| --- | --- | --- |
| Thread | `Conversation` | 保留现有表和业务名称，对外协议增加 `threadId` 映射 |
| Turn | 当前用户消息及其执行链 | 新增稳定 Turn 标识，关联用户消息和一个或多个 Run |
| Run | `agent_runs` / `Task` | `agent_runs` 作为调度实例，Task 作为业务执行实例，协议层统一关联 |
| Item | Message、Tool Call、Approval、Artifact 等 | 新增统一 Item/Event 契约，不立即合并现有业务表 |
| Event | SSE 事件、Trace、状态变更 | 建立不可变、单调有序的运行事件事实源 |

### 4.2 目标执行链

```text
用户或业务系统
    -> Runtime API / SDK
    -> Thread / Turn / Run
    -> AgentLoopKernel
       -> ContextProvider
       -> ExecutionModeStrategy
          -> workflow_plan
          -> interactive_loop
       -> ToolRuntime
       -> CapabilityPolicy
       -> ApprovalCoordinator
       -> CheckpointStore
       -> RuntimeEventSink
    -> Projection / Query Model
    -> 前端、运营后台或业务系统
```

### 4.3 统一事件 Envelope

计划使用以下稳定结构，具体字段在数据库设计阶段确认：

```json
{
  "eventId": "EVT_xxx",
  "sequenceNo": 12,
  "threadId": "CONV_xxx",
  "turnId": "TURN_xxx",
  "runId": "RUN_xxx",
  "taskId": 1001,
  "itemId": "ITEM_xxx",
  "eventType": "tool.call.completed",
  "occurredAt": "2026-08-31T10:00:00+08:00",
  "payloadVersion": 1,
  "payload": {},
  "metadata": {}
}
```

首批事件类型：

1. `turn.created`
2. `run.queued / run.started / run.completed / run.failed / run.cancelled`
3. `message.delta / message.completed / message.replaced`
4. `plan.created / plan.replanned`
5. `step.started / step.completed / step.failed / step.waiting`
6. `tool.call.requested / tool.call.started / tool.call.completed / tool.call.failed`
7. `approval.requested / approval.resolved / approval.revoked`
8. `artifact.created / artifact.published`
9. `checkpoint.created / checkpoint.restored`
10. `replay.started / replay.completed / replay.failed`

事件中不得保存模型思维链，只保存安全进度、输入输出快照引用和可审计元数据。

---

## 5. 分阶段实施计划

## 5.1 阶段 0：协议和状态机冻结

目标：在改代码前确定稳定概念和兼容边界。

任务：

1. 输出 Runtime Protocol 规范
2. 明确 Thread、Turn、Run、Task、Step、Item、Event 的关系
3. 明确 Run、Task、Approval、Wait、Replay 状态机
4. 明确事件顺序、幂等键、重复消费和断线续传规则
5. 明确旧 SSE 到新事件的兼容映射
6. 明确事件 Payload 脱敏和最大长度规则
7. 完成数据库表、字段、索引和 RLS 设计，并同步更新 `docs/数据库设计.md`

交付物：

1. `docs/runtime/Runtime-Protocol规范.md`
2. `docs/runtime/Runtime状态机.md`
3. 数据库设计更新
4. API 与事件示例

验收标准：

1. 每个现有同步、异步、审批、等待、恢复链路都能映射到协议
2. 不存在同一状态在 Run、Task、Step 中语义冲突
3. 旧接口兼容策略可执行

预计工作量：2 至 3 个开发日

## 5.2 阶段 1：统一 Runtime Event Protocol

目标：建立单一、持久化、可断线重连的运行事件出口。

当前进度：已完成 `RuntimeEventEnvelope / RuntimeEventPublisher`、消息和进度事件、Run / Plan / Step / Tool / Approval / Artifact / Checkpoint 生命周期事件、历史事件兼容映射和按 `Last-Event-ID` 续传的新 SSE 接口。Run 终态更新与事件写入已使用同一事务；Tool 事件通过稳定 `toolCallId` 关联且不保存参数值和输出正文；Approval 事件与审批状态原子提交，审批续跑恢复原 Run 作用域；Artifact 事件通过确定性 `artifactId` 关联生成和发布阶段，事件不保存文件正文并与最终回复共享下载地址事实源；Checkpoint 创建与快照同事务提交，恢复事件只在普通恢复或审批续跑真正加载快照时发布；Plan 事件与不可变计划版本同事务提交，重规划事件只携带版本关系和安全差异摘要。会话页已切换统一消费器，按 `sequenceNo` 幂等消费并自动续传，统一驱动聊天增量、执行进度、审批弹窗、产物提示和 Run 终态，同时保留轮询降级。已补齐 M1 事件编码契约、非法游标、`Last-Event-ID` 优先级、统一 SSE、旧 SSE 投影以及浏览器断线续传和审批/产物投影测试。前端支持 `auto / unified / legacy` 三种协议模式、Agent 白名单和稳定百分比灰度，`auto` 在统一协议不可用时自动回退旧 SSE；本地 Docker 已完成双后端重建、前端发布、数据库迁移和健康检查。

后端任务：

1. 新增 `RuntimeEventEnvelope`、`RuntimeEventType` 和 Payload 版本契约
2. 新增 `RuntimeEventPublisher` SPI
3. 提供 PostgreSQL Event Store 实现
4. 将现有 `AgentRunStreamEventService` 适配为 Runtime Event 投影
5. 将消息、步骤、Tool、审批、产物和终态接入统一事件发布器
6. 新增按游标读取的 `/api/core/runtime/runs/{runCode}/events` SSE 接口
7. 保留旧 Run Stream API，由兼容投影继续输出旧事件

前端任务：

1. 新增统一 Runtime Event 消费器
2. 根据事件驱动聊天增量、执行进度、审批弹窗、产物和终态
3. 保留轮询降级
4. 断线后使用 `sequenceNo` 继续消费

建议代码边界：

```text
agent-core/.../modules/runtime/protocol/
agent-core/.../modules/runtime/event/
frontend-web/src/runtime/
```

验收标准：

1. 普通问答、单 Tool、PDF 产物、审批、等待和失败链路均有完整有序事件
2. SSE 断开后可以从最后游标恢复且不丢不重
3. 审批控制状态不再作为聊天消息出现
4. 新旧 SSE 在兼容期结果一致
5. Event Store 写入失败时不得伪造成功终态

预计工作量：5 至 7 个开发日

## 5.3 阶段 2：可验证 Replay

目标：区分 Prompt 回放、模拟重放和真实执行重放，先交付无副作用能力。

当前进度：M2 已完成。七个纵向切片已交付 Replay 模式和独立审计、安全 Prompt Replay、录制结果 Simulation Replay、结构化 Event Diff、Artifact 内容/元数据校验、独立管理页、浏览器端到端测试，以及后端总开关/模式开关/Project-Agent 白名单/稳定百分比灰度。成功 Replay 冻结 `featureDecision`，关闭执行入口仍保留历史审计查询；`execution_replay` 固定不开放。发布与回滚见 `docs/runtime/Replay发布与回滚.md`。

Replay Mode：

1. `prompt_replay`：使用历史 Prompt 快照重新调用 LLM，即当前能力
2. `simulation_replay`：复用历史 Tool、Workflow、审批和产物事件，只重新运行决策或结果组装
3. `execution_replay`：重新执行允许重放的步骤，外部副作用必须通过幂等或审批

首批只实施：

1. 正式将当前 Trace Replay 标识为 `prompt_replay`
2. 实施 `simulation_replay`
3. 为 `execution_replay` 预留契约，不开放生产入口

任务：

1. 固化 Run Manifest，包括模型、资源版本、Prompt、Tool Schema、Policy 和执行模式摘要
2. 按事件范围构建不可变 Replay Input
3. 新增 `ReplayEngine` 和 `ReplayModeHandler`
4. 将历史 Tool 结果作为录制结果注入，禁止触发 Provider
5. 输出原结果、新结果、事件 Diff 和不一致原因
6. Replay 产生独立 Run/Trace，不修改原任务
7. 建立 Artifact 内容哈希和文件元数据校验
8. 建立 Replay 管理页面和只读审计查询入口
9. 补齐 Replay 管理页浏览器端到端回归
10. 增加后端 Feature Flag、灰度决策审计和安全回滚路径

验收标准：

1. `simulation_replay` 不产生网络写入、文件写入或数据库业务写入
2. 可以证明使用的是录制 Tool 结果而不是重新调用
3. Replay 结果可与原 Run 做结构化 Diff
4. 原 Run、原 Trace 和原产物保持不可变
5. 缺少关键快照时明确失败，不做静默降级
6. 管理页面可检索 Replay，并明确区分来源快照与 Replay 独立结果

M2 完成审计：上述 10 项均已完成。Replay 模式、不可变 Manifest/Input、Simulation 录制事实注入、结构化 Diff、Artifact 哈希校验、管理页面、浏览器回归、后端灰度开关和回滚手册均已落地；`execution_replay` 按计划只保留契约。

预计工作量：5 至 8 个开发日

## 5.4 阶段 3：AgentLoopKernel 内核拆分

目标：将执行循环与业务持久化、步骤细节、审批、事件实现解耦。

当前进度：M3 十二个纵向切片及最终审计已全部完成。第十二批新增稳定 `RuntimeCheckpoint / RuntimeCheckpointForkResult`，Checkpoint Store、PostgreSQL 持久化实现、Conversation 恢复链和 HTTP 返回已统一使用独立 Runtime model；查询、自动恢复、恢复标记、歧义处置和分支创建不做 Adapter 映射，历史 JSON 字段与对象语义保持不变。Kernel 契约已不再反向依赖具体 Orchestrator 或 Checkpoint Service 返回类型。

计划新增接口：

1. `AgentLoopKernel`
2. `ExecutionModeStrategy`
3. `RuntimeContextProvider`
4. `RuntimeStepExecutor`
5. `ObservationPolicy`
6. `CheckpointStore`
7. `RuntimeEventSink`
8. `ApprovalCoordinator`
9. `RuntimeResponseAssembler`

迁移策略：

1. 先以 Adapter 包装现有 Service，不复制业务逻辑
2. `ExecutionStepHandlerRegistry` 继续作为步骤执行扩展点
3. `ExecutionCheckpointService` 作为首个 `CheckpointStore` 实现
4. `AgentRunStreamEventService` 迁移为 Event Projection，不再作为核心事件源
5. `ExecutionOrchestratorService` 逐步只保留循环控制，不承载具体快照 JSON 拼装
6. 新旧内核通过 Feature Flag 对相同测试用例执行结果对比

首个纵向切片说明：

1. 已建立内核调用缝和旧 Orchestrator Adapter
2. 已接入默认关闭的总开关、Agent 白名单和稳定百分比灰度
3. 已验证关闭走旧直接路径、开启走 Adapter 且不会双执行
4. 该切片完成时尚未迁移 `RuntimeContextProvider / RuntimeStepExecutor / ObservationPolicy` 等职责

第二个纵向切片说明：

1. 已建立 `RuntimeContextProvider` 和默认解析实现
2. 已将多租户 / Project 作用域冻结到 `ExecutionContext`，不依赖线程本地变量做内核路由
3. 已补齐 Project 白名单和 Project + Agent 稳定分桶
4. 该切片完成时尚未迁移 `RuntimeStepExecutor / ObservationPolicy / CheckpointStore` 等职责

第三个纵向切片说明：

1. 已建立 `RuntimeStepExecutor / RuntimeStepExecutionRequest` 契约
2. 已将 Workflow、Approval 和普通 Step Handler 路由收口到 `LegacyRuntimeStepExecutorAdapter`
3. `ExecutionOrchestratorService` 不再直接依赖 `WorkflowExecutorService / ApprovalStepService / ExecutionStepHandlerRegistry`
4. Workflow 恢复上下文、输出快照、Approval 挂起异常和普通 Handler 结果保持原契约
5. 该切片完成时尚未迁移 `ObservationPolicy / CheckpointStore / RuntimeEventSink` 等职责

第四个纵向切片说明：

1. 已建立 `ObservationPolicy / ObservationPolicyRequest` 契约
2. 已通过 `LegacyObservationPolicyAdapter` 复用现有规则、LLM 决策和 fallback 语义
3. `ExecutionOrchestratorService` 不再直接依赖 `ObservationDecisionService`
4. `step_decision` Trace、`finish`、`replan` 异常和成功 Checkpoint 仍保留在循环控制层
5. 该切片完成时尚未迁移 `CheckpointStore / RuntimeEventSink / ApprovalCoordinator` 等职责

第五个纵向切片说明：

1. 已建立 `CheckpointStore / CheckpointSaveRequest` 契约
2. 已通过 `LegacyCheckpointStoreAdapter` 复用现有 PostgreSQL 持久化和 Checkpoint Runtime Event
3. Orchestrator 与 Conversation 恢复主链均不再直接依赖 `ExecutionCheckpointService`
4. 保存、查询、自动恢复、恢复标记、副作用歧义处置和 Checkpoint 分支均纳入存储契约
5. 当前 `Checkpoint / ForkResult` 暂时复用旧服务类型，后续再迁移为 Kernel model
6. 该切片完成时尚未迁移 `RuntimeEventSink / ApprovalCoordinator / RuntimeResponseAssembler` 等职责

第六个纵向切片说明：

1. 已建立 `RuntimeEventSink` 及 Progress、StepStarted、StepTransitioned、ApprovalRequested 命令契约
2. 已通过 `LegacyRuntimeEventSinkAdapter` 复用现有进度、Step 和 Approval 事务 Service
3. `ExecutionOrchestratorService` 不再直接依赖 `AgentRunStreamEventService / TaskStepLifecycleEventService / ApprovalRuntimeEventService`
4. 进度投影 best-effort、Step / Approval 原子写入、Artifact 联动和异常语义保持不变
5. Checkpoint Event 仍在 `CheckpointStore` 内发布，避免拆分事务或重复事件
6. 尚未迁移 `ApprovalCoordinator / RuntimeResponseAssembler`，也尚未将 Event Projection 与核心 Event Store 实现完全分层

第七个纵向切片说明：

1. 已建立 `ApprovalCoordinator` 及 ApprovalRequest、ApprovalTransition、Run Scope 契约
2. 已通过 `LegacyApprovalCoordinatorAdapter` 复用现有 Approval 状态、Runtime Event 与 Run 作用域实现
3. `ExecutionOrchestratorService / ConversationService / ConversationController` 不再直接依赖 `ApprovalRuntimeEventService`
4. Approval 请求、通过、拒绝、超时和撤回仍由原事务 Service 保证状态与 Runtime Event 原子提交
5. 审批 HTTP 续跑仍恢复原 Run，审批弹窗与聊天控制消息语义不变
6. 尚未迁移 `RuntimeResponseAssembler`，也尚未将 Event Projection 与核心 Event Store 实现完全分层

第八个纵向切片说明：

1. 已建立 `RuntimeResponseAssembler / ResponseAssemblyRequest` 契约
2. 已通过 `LegacyRuntimeResponseAssemblerAdapter` 复用现有计划响应汇总和 Artifact 一致性实现
3. `ExecutionOrchestratorService` 不再直接依赖 `ExecutionResponseAssemblerService / ArtifactResponseConsistencyService`
4. terminal reply、单响应片段与 LLM 汇总优先级保持不变，不重复生成已完成回复
5. 最终响应汇总和 Artifact 收口共享原流式 Scope，异常继续上抛并恢复线程现场
6. Artifact 事实校正、受保护下载链接和 published 事件语义保持不变
7. Step 内 reply / reflection 仍属于步骤执行实现；尚待 M3 最终审计、过渡类型收口及 Event Projection 与核心 Event Store 分层评估

第九个纵向切片与最终审计第一批说明：

1. 已新增独立 `RuntimeStepResultContext`，移除 `ExecutionOrchestratorService.StepResultContext`
2. Observation、Checkpoint、Response、恢复、Workflow 和步骤执行链统一使用新模型
3. 字段名、构造顺序和 JSON 快照结构保持不变，历史 Checkpoint 无需数据库迁移
4. Event Store 核心写入继续通过 `RuntimeEventPublisher`，PostgreSQL 实现为 `PersistentRuntimeEventPublisher`
5. `RuntimeEventQueryService` 负责统一 Envelope 投影，`AgentRunStreamEventService` 仅作为模型流发布与旧 SSE 查询兼容门面
6. 仍待收口 `AgentLoopExecutionRequest / Result` 的旧 Orchestrator 类型
7. 仍待收口 `RuntimeStepExecutionRequest / RuntimeStepExecutor` 的旧步骤执行 DTO
8. 仍待收口 `CheckpointStore` 的旧 Checkpoint / ForkResult 返回类型

第十个纵向切片与最终审计第二批说明：

1. 已新增稳定 `RuntimeExecutionRequest / RuntimeExecutionAggregate`
2. Recovery 与 Approval Replay 快照已迁入稳定执行请求，不再属于 Orchestrator 嵌套类型
3. `AgentLoopExecutionRequest` 字段由 `legacyRequest` 收口为 `executionRequest`
4. Conversation、Gateway、Orchestrator 和 Checkpoint 保存链统一使用同一执行请求对象
5. Feature Flag 关闭仍走同一 Orchestrator 直接路径，开启仍经 Adapter 单次委托，不影子双执行
6. `waitingApproval / waitingEvent / paused` 状态判定和助手内容、实际计划保持原契约
7. 仍待收口 `RuntimeStepExecutionRequest / RuntimeStepExecutor` 的旧步骤执行 DTO
8. 仍待收口 `CheckpointStore` 的旧 Checkpoint / ForkResult 返回类型

第十一个纵向切片与最终审计第三批说明：

1. 已新增稳定 `RuntimeStepExecutionCommand / RuntimeStepExecutionOutcome`
2. `RuntimeStepExecutionRequest` 字段由过渡 `stepRequest` 收口为稳定 `command`
3. Plan、Workflow 与 `ExecutionStepHandlerRegistry` 已统一使用稳定单步执行模型
4. `RuntimeStepExecutor` 契约不再引用步骤 Service 层 DTO，旧 DTO 已删除
5. Workflow 恢复上下文仍独立透传，输出快照字段及顺序保持不变
6. Approval 挂起异常继续原样上抛，普通 Handler 请求和结果继续保持同一对象语义
7. 仍待收口 `CheckpointStore` 的旧 Checkpoint / ForkResult 返回类型

第十二个纵向切片与 M3 最终审计说明：

1. 已新增稳定 `RuntimeCheckpoint / RuntimeCheckpointForkResult`
2. `ExecutionCheckpointService` 已直接生产和消费稳定模型，移除嵌套 `Checkpoint / ForkResult`
3. `CheckpointStore` 契约不再引用具体 Checkpoint Service 返回类型
4. `LegacyCheckpointStoreAdapter` 对查询、自动恢复、恢复标记、歧义处置和分支结果继续同对象透传
5. Conversation 恢复、审批恢复、Checkpoint 歧义处置和分支入口已统一使用稳定模型
6. HTTP 响应字段、数据库查询与 JSON 快照结构不变，无需数据库迁移
7. Kernel 计划内九个可替换职责及稳定跨层模型已全部完成，M3 最终审计通过

验收标准：

1. Orchestrator 不直接依赖具体 Tool Provider、前端事件格式或审批页面语义
2. 新增 Step Handler 不需要修改内核循环
3. Checkpoint 和事件存储可以使用测试内存实现替换
4. 现有主要 E2E 链路行为不变
5. 新旧内核在相同输入下的终态、步骤和产物一致

预计工作量：7 至 10 个开发日

## 5.5 阶段 4：统一 Runtime Plugin 规范

目标：从“外部 Tool Jar”升级为声明式、可治理的运行插件。

当前进度：已完成 M4 Runtime Plugin 阶段六个纵向切片。第六批完成最终审计和真实发布验证：Docker 发布链已构建、挂载外部插件 Jar 并设置 `loader.path`；平台 E2E 使用真实 Jar 验证 Registry 版本/安装、Core SHA-256 对账、`loaded` 回写及租户内审计。Runtime Plugin 当前范围完成，M4 下一批进入 Capability Policy。

插件首批可包含：

1. `tools`
2. `toolProviders`
3. `stepHandlers`
4. `contextProviders`
5. `policies`
6. `hooks`
7. `skills`

Manifest 建议字段：

```json
{
  "pluginCode": "example-plugin",
  "version": "1.0.0",
  "platformApiVersion": "1",
  "entrypoint": "com.example.ExampleAgentPlugin",
  "capabilities": [],
  "dependencies": [],
  "components": {}
}
```

任务：

1. 定义 `AgentRuntimePlugin` SPI 和 Manifest Schema
2. 将当前 Tool Executor 自动发现适配到新 SPI
3. 增加插件版本兼容、组件冲突和依赖校验
4. Registry 增加插件定义、版本、安装和 Agent 绑定能力
5. Core 增加插件加载结果、健康状态和依赖观测
6. 第一阶段继续使用重启加载，不承诺 JVM ClassLoader 热卸载
7. 后续再增加 `draining -> disabled -> unload` 生命周期

第一个纵向切片说明：

1. 已定义 `AgentRuntimePlugin` SPI，冻结当前 `platformApiVersion=1` 和 Jar 内 Manifest 固定路径
2. 已定义不可变 `RuntimePluginManifest / RuntimePluginDependency` 模型
3. 已锁定 `tools / toolProviders / stepHandlers / contextProviders / policies / hooks / skills` 七类组件编码
4. 已发布 Draft 2020-12 JSON Schema，并保持 Java 模型与 Schema 字段一致
5. 已增加基础格式、重复值和未知组件类型校验；跨插件版本兼容、组件冲突和依赖可用性留到第三个任务
6. 当前不改变 `ToolExecutor` Spring Bean 自动发现，不新增数据库，不支持热卸载

第二个纵向切片说明：

1. 已新增 `RuntimePluginDiscovery`，通过 Java `ServiceLoader` 从线程上下文 ClassLoader 发现插件入口
2. 插件按 `pluginCode / version / entrypoint` 稳定排序并暴露不可变列表
3. `ToolExecutorRegistry` 已接入插件发现结果，建立 Tool code 与插件编码的不可变归属映射
4. Tool 解析仍使用原 Spring Bean 列表，原 Executor 实例、冲突错误和 `ToolRouterService` 治理链不变
5. `agent-tool-plugin-gaokao` 已提供 `GaokaoAgentRuntimePlugin`、Jar Manifest 和 Java SPI Provider 文件
6. 已通过真实插件 Jar 验证 SPI 可发现 `gaokao-tool-plugin` 及其 5 个 Tool 声明
7. 当前不校验跨插件冲突、平台 API 兼容和依赖可用性，由下一个纵向切片统一启动阻断

第三个纵向切片说明：

1. 已新增 `RuntimePluginValidator`，并在 `RuntimePluginDiscovery` 完成发现后、组件注册前执行
2. 已校验平台 API 主版本、Manifest 入口、插件编码和全部七类组件的跨插件唯一性
3. 已校验必需/可选依赖、已安装版本范围和依赖环
4. 已新增无第三方依赖的 `RuntimePluginVersionMatcher`，支持精确、Caret、Tilde、比较区间、通配符、或关系及 SemVer 预发布排序
5. Manifest 声明 Tool 必须恰好匹配一个 Spring `ToolExecutor`，缺失或重复均在启动期阻断
6. 校验失败统一抛出带稳定 `errorCode + details` 的 `RuntimePluginValidationException`
7. 高考插件 Manifest 通过完整启动校验；原 Tool 路由、异常和执行实例语义不变

第四个纵向切片说明：

1. Registry 新增 `runtime_plugins / runtime_plugin_versions / runtime_plugin_installations / agent_runtime_plugin_rel / runtime_plugin_lifecycle_audits`
2. 插件编码在 `tenant_id + project_id` 作用域唯一，全部新表启用 PostgreSQL RLS
3. 版本保存不可变 Manifest、制品 URI 和 SHA-256，只允许发布状态转换
4. 同一插件和环境只保留一个当前安装，版本替换及启停都标记 `pending_restart`
5. Agent 绑定到已启用的具体安装，避免运行时插件版本和环境歧义
6. 仍被 Agent 绑定的安装不允许停用或卸载，已启用安装不允许废弃对应版本
7. 定义、版本、安装、启停和 Agent 绑定变更全部写入安全审计快照

第五个纵向切片说明：

1. Registry 新增按 `environmentCode` 查询期望安装和按安装 ID 接收加载结果的服务间 API
2. Core 启动完成后执行一次期望/实际对账，匹配插件编码、版本和可计算的 Jar SHA-256
3. 实际结果回写 `loaded / failed`、观测编码、观测版本、观测摘要、错误摘要和最近上报时间，并写入生命周期审计
4. Core 暴露插件总览、单插件详情和手动重试 API，观测依赖、组件实现、ClassLoader 与制品来源
5. 可选依赖缺失单独计数；本地未登记插件和回写失败进入 `degraded`，Registry 读取失败进入 `failed`，均不阻断 Core 启动
6. Actuator 组件保持 `UP` 并在明细中暴露插件治理 `UP / DEGRADED`，避免控制面短暂故障影响数据面存活探针
7. 当前仍坚持重启加载；本切片只观测并收敛 `pending_restart`，不实现 JVM 热加载或热卸载

第六个纵向切片与最终审计说明：

1. 修正本地 Docker 发布链，统一构建 Core、Registry 和高考插件，将插件 Jar 只读挂载到 `/app/plugins`
2. Core 容器通过 `JAVA_TOOL_OPTIONS=-Dloader.path=/app/plugins` 使用 `PropertiesLauncher` 发现真实插件
3. 发布脚本显式传入插件环境编码，并在健康检查后校验启动日志包含插件编码和版本
4. 修正服务身份回写加载审计时的租户作用域，审计从安装记录继承 Tenant、Organization、Workspace 和 Project
5. 平台 E2E 创建真实插件定义、不可变版本和环境安装，校验 Jar SHA-256、Core 对账、`loaded` 状态与租户内审计
6. 新增 `docs/runtime/RuntimePlugin发布与回滚.md`，冻结发布前登记、Docker 发布、验收和版本回滚流程
7. Runtime Plugin 当前范围完成；“未授权 Capability 无法调用运行能力”属于后续 Capability Policy，不在插件装载层伪实现

验收标准：

1. 现有高考领域插件可迁移到新 Manifest 且核心无业务代码回流
2. 未授权 Capability 的插件无法调用对应运行能力
3. 版本不兼容、组件冲突或缺依赖时启动明确失败
4. 插件安装、启停和依赖关系可审计

预计工作量：7 至 10 个开发日

## 5.6 阶段 5：Capability Policy、Sandbox 和审批下沉

目标：审批由平台策略决定，不由 Planner 文案或业务 Workflow 猜测。

当前进度：四个纵向切片已完成。除声明契约、调用前决策和稳定 Tool Call 外，已将 `policy.decided` 复用为全量决策审计事实源，提供用户隔离的分页、指标、聚合、详情事件链和独立运营页面。Capability Policy 当前范围完成。

Capability 首批分类：

1. `filesystem.read`
2. `filesystem.write`
3. `network.read`
4. `network.write`
5. `process.execute`
6. `database.read`
7. `database.write`
8. `artifact.publish`
9. `external.side_effect`

任务：

1. Tool / Plugin 声明 Capability 和 Side Effect
2. 新增 `CapabilityPolicyEngine`
3. Tool 调用前根据用户、Agent、项目、参数和目标资源计算决策
4. 输出 `allow / sandbox / approval / block`
5. Approval Request 通过 Runtime Event 发送给会话 UI
6. 审批通过后使用原 Tool Call ID 和参数哈希继续执行
7. 保留 Workflow 显式业务审批，两类审批使用同一协议但来源不同

第一个纵向切片说明：

1. `tools.runtime_capabilities` 独立保存安全能力声明，不复用面向 Planner 的 `metadata.routing.capabilities`
2. `tools.side_effect_level` 成为副作用权威字段，详细幂等与补偿配置仍保留在 `metadata.sideEffectPolicy`
3. Registry Tool 写入拒绝未知、重复 Capability，并强制顶层与 metadata 副作用等级一致
4. Runtime Plugin Java Manifest、JSON Schema 和 Registry 版本创建统一只接受九类 Capability
5. Agent Tool API 和 Core `RegistryAgentTool` 已携带新字段；Core 以顶层副作用等级执行，历史快照可回退旧 metadata
6. Tool 管理页可多选安全能力并查看副作用等级；内置 Tool 已按实际边界预置声明
7. 详细契约见 `docs/runtime/CapabilityPolicy规范.md`

第二个纵向切片说明：

1. `CapabilityPolicyEngine` 只消费 Registry 可信声明和策略快照，不信任 Planner 生成的风险文案
2. 显式策略支持 Capability、Side Effect、Project、参数条件、目标路径与目标前缀，按优先级确定主决策
3. 未命中显式策略时使用只读放行、已声明隔离边界内执行、写入/副作用审批、非法声明阻断的安全基线
4. `ToolRouterService` 在副作用幂等占用和 Provider 调用前发布 `policy.decided` 并执行 `allow / sandbox / approval / block`
5. 参数和目标只以 SHA-256 摘要进入事件与审批元数据；审批节点同时绑定策略版本、能力、副作用和参数哈希
6. Plan 与 Workflow 的 Capability 审批均恢复当前 Tool 节点，已通过的前置 Guardrail 不会重复弹出
7. Registry 和管理前端允许 Capability Policy 使用 `allow / sandbox`，并校验 metadata 中的能力、参数和目标规则

第三个纵向切片说明：

1. `ToolRouterService` 在 Capability 决策后、Provider 调用前分配 Tool Call ID，并写入脱敏决策事实
2. Capability 审批记录持久化 `toolCallId + parameterHash`，不保存额外参数或目标资源原文
3. 审批续跑从原审批记录恢复调用标识，节点或参数哈希不一致时拒绝复用
4. `approval.requested / resolved` 仅投影格式合法的 Tool Call ID 和 SHA-256 参数哈希，继续排除审批意见和完整 metadata
5. `ToolRuntimeEventService` 支持使用预分配 ID，审批后的 `tool.call.requested / started / completed / failed` 延续原调用标识
6. 统一事件查询将 `policy.decided` 和 `tool.call.*` 的 `itemId` 映射为同一 Tool Call ID
7. 旧审批记录缺少调用标识时保持兼容，续跑生成新 ID，不影响已有审批恢复

第四个纵向切片与当前范围最终审计说明：

1. 不新建重复审计表，`policy.decided` 继续作为 Capability Policy 不可变决策事实
2. 新增当前用户隔离的分页、指标和详情 API，支持决策、Tool、Capability、Side Effect、策略、Agent、Run、Trace 和时间筛选
3. 指标覆盖四类决策、审批续跑、Run/Tool 去重数、Capability 和 Tool 分布
4. 详情按 `runCode + toolCallId` 关联 `policy.decided / approval.* / tool.call.*`，恢复后仍是同一调用链
5. 页面只展示安全 Payload、参数/目标哈希，不返回 Tool 参数、输出正文或审批意见
6. `agent_run_stream_events` 增加 `policy.decided` 部分索引，真实 PostgreSQL/Flyway E2E 覆盖分页和聚合 SQL
7. Runtime Plugin 与 Capability Policy 当前范围验收完成，下一阶段进入 `workflow_plan / interactive_loop` 双执行模式

验收标准：

1. 同一个 Tool 可根据实际参数产生不同风险决策
2. PDF 沙箱生成和外部发布可以分别判定风险
3. LLM 无法通过生成低风险步骤名称绕过策略
4. 审批通过后弹窗关闭、事件恢复且不重复执行已成功步骤
5. 所有策略决策进入 Trace 和审计记录

预计工作量：5 至 7 个开发日

## 5.7 阶段 6：双执行模式

当前进度：八个纵向切片已完成，M5 当前范围完成。已冻结配置、路由、预算和停止协议，完成 Interactive Loop 控制器、审批 Checkpoint 恢复、Kernel Adapter、同步/异步 E2E、灰度指标和紧急回滚。

目标：固定流程保持确定性，探索型任务获得受限的动态 Tool Loop。

执行模式：

1. `workflow_plan`：沿用 Decision、Planner、Plan、Step 和 Workflow
2. `interactive_loop`：模型按 Observation 动态决定下一次 Tool 或完成回复

`interactive_loop` 强制限制：

1. 最大迭代次数
2. 最大 LLM 调用数
3. 最大 Tool 调用数
4. 最大 Token 和成本
5. 允许 Tool 集合
6. Capability Policy
7. 重复调用检测
8. 明确停止原因

路由原则：

1. 已绑定确定性 Workflow 或明确审批流程时优先 `workflow_plan`
2. 探索、检索、排错和多轮 Tool 调查可选择 `interactive_loop`
3. 高风险任务不能仅因使用 `interactive_loop` 跳过显式 Workflow

验收标准：

1. 两种模式使用同一 Runtime Event、Tool Runtime、Checkpoint 和 Approval
2. 动态循环达到预算或重复阈值时确定性停止
3. 现有 Workflow 行为和发布契约不变
4. 同一 Agent 可配置默认模式和允许的备选模式

预计工作量：7 至 10 个开发日

第一个纵向切片说明：

1. Agent `metadata.executionPolicy` 新增 `defaultMode / allowedModes`，历史 Agent 默认 `workflow_plan`
2. Registry 写入时校验模式枚举、重复值、默认模式归属，并强制保留 `workflow_plan` 安全回退路径
3. 已选择 Workflow 或计划包含 Approval 时确定性使用 `workflow_plan`
4. `interactive_loop` 执行器尚未具备预算和停止保护，因此当前只接受配置并明确回退，不开放不受控执行
5. 每次路由发布不含用户正文和 Tool 参数的 `run.mode.selected`，记录请求模式、实际模式、允许集合、原因和是否回退
6. 现有 Kernel Adapter 灰度、Orchestrator、Workflow、Approval、Checkpoint 和返回契约不变
7. 下一切片实现 Interactive Loop 预算与停止原因模型，再接入最小动态循环

第二个纵向切片说明：

1. Agent `executionPolicy.interactiveLoop` 支持最大迭代、LLM、Tool、Token、成本和重复 Tool 指纹上限
2. Registry 校验所有字段范围，并保证 Loop LLM 上限不超过整次 Run 上限
3. Core 对历史非法配置安全回退，Loop LLM 预算始终取 Loop 与 Run 的更严格值
4. 新增原子预算状态机，所有调用前先判断再记账，超限调用不污染已消费计数
5. 相同 Tool 与参数哈希达到阈值后以 `duplicate_tool_call` 确定性停止；对外快照不暴露参数哈希或内部指纹
6. 冻结十一类停止原因并新增脱敏 `run.loop.stopped` 事件契约，首次停止原因不可覆盖
7. 全局 CostGovernance 与共享 LLM 预算保持外层门禁，局部预算不替代现有成本账本
8. 下一切片接入最小 Interactive Loop 控制器和结构化 Action

第三个纵向切片说明：

1. 冻结 `tool_call / final_answer` 两类 JSON Action，不接收 rationale 或自由文本
2. 默认 LLM 决策器只向模型提供安全 Tool 字段、输入 Schema、用户目标、会话状态和最近有界 Observation，不提供认证配置
3. 控制器在物理 LLM 调用前估算 Token 和当前模型价格，原子占用局部预算后再进入现有 LLM Gateway
4. Tool 必须属于 `ExecutionContext.availableTools`，参数按递归键排序计算稳定 SHA-256 后再做重复检测
5. Tool 执行复用 `ToolRouterService`，Capability Policy、Approval、Side Effect、CostGovernance、Fallback 和 Tool Runtime Event 均不复制、不绕过
6. Tool 普通失败转为有界 Observation；越权或策略阻断确定性停止；审批先发布 Loop 停止事实再进入原审批异常链
7. 最终结果返回稳定停止原因、安全预算快照和有界 Observation，完整 Tool 输出快照不进入 Loop Result
8. 当前控制器未接生产路由；下一切片增加灰度开关与 Kernel Adapter，并先解决审批恢复 Checkpoint

第四个纵向切片说明：

1. 新增独立 `interactive_loop_checkpoints`，不复用强绑定 Plan/Step 的 `task_execution_checkpoints`
2. 审批挂起前持久化原始预算策略、累计额度、不可逆 Tool 指纹、已完成有界 Observation 和待执行 Tool Action
3. Checkpoint 保存 Capability 审批节点、`toolCallId` 与 `parameterHash`，恢复前必须和已批准记录逐项一致
4. 审批通过后在 `CapabilityApprovalContext` 内只重放挂起 Tool，不再次消费 Tool 额度，也不重新调用审批前已成功 Tool
5. 待执行 Tool 完成或形成失败 Observation 后将 Checkpoint 单向标记为 `restored`，随后从下一轮模型决策继续
6. 快照按租户和项目隔离并启用 PostgreSQL RLS；公开预算事件仍不暴露内部 Tool 指纹
7. 生产路由继续关闭；下一切片增加独立 Feature Flag、稳定灰度与 `AgentLoopKernel` Adapter

第五个纵向切片说明：

1. 新增独立 `core.runtime.interactive-loop` Feature Flag，与旧 Orchestrator Adapter 灰度开关完全分离
2. 支持 Project 白名单、Agent 白名单和基于 `agentId + projectId` 的 SHA-256 稳定百分比分桶，默认关闭且灰度比例为 0
3. `RuntimeExecutionModePolicy` 在 Workflow、显式 Approval 和 Agent 模式约束之后应用灰度，不满足条件确定性回退 `workflow_plan`
4. 新增 `interactive_loop_kernel_v1` Adapter，由 `AgentLoopExecutionGateway` 按实际业务执行模式选择对应 Kernel
5. Adapter 使用现有 RuntimeEventSink 创建/迁移锚点 Task Step，并使用现有 ApprovalCoordinator 创建会话内审批
6. 审批挂起同步保存通用 Plan Checkpoint 和 Loop Checkpoint，使既有 `approveTask` 恢复入口加载原 Plan 后进入 Loop Checkpoint
7. `run.mode.selected` 增加安全的 `rolloutBucket`，不包含用户输入或 Agent 私密配置
8. 下一切片进行本地灰度 E2E，验证真实 LLM Tool Loop、审批弹窗与恢复后最终回复，再决定扩大灰度

第六个纵向切片说明：

1. 本地仅对白名单 Agent 7 开启 `interactive_loop`，真实 LLM 已完成 `TIME_INFO` Tool Loop 和 `max_tokens` 停止验证
2. E2E 发现历史 Catalog 安装的 MCP `write_file` 沿用 `[] / none`，会被 Capability 只读基线误放行
3. Registry Catalog 安装现按远程 operation 推断最低能力；Flyway 回填历史 MCP 文件写操作，并保留 `sideEffectPolicy` 一致性
4. Core 对旧 Registry 快照增加只提升风险的 MCP operation 兜底，文件写操作至少按 `filesystem.write / irreversible` 进入审批
5. 真实对话验证 Tool 在审批前未写文件，对话窗口自动弹出审批；点击“通过并继续”后弹窗关闭、Checkpoint 转为 `restored`、Tool 写入并生成最终回复
6. 默认 Feature Flag 和百分比灰度仍保持关闭；下一切片补异步 Run、流式事件和取消边界的真实灰度回归，再评估扩大范围

第七个纵向切片说明：

1. 真实异步 Run 已验证 `run.queued -> run.started -> run.waiting`、Capability 审批、Checkpoint 恢复和后续 Tool 事件共用同一 `run_code`
2. 修复人工审批/恢复只同步 `agent_runs.status`、却没有追加恢复后终态事件的问题；状态迁移与 `run.completed / run.failed / run.waiting` 现在同事务提交
3. 重复同步相同 Run 状态不会再次写终态事件，客户端可从原 `run.waiting` 游标继续 Replay 新的 `approval.resolved -> run.completed`
4. Interactive Loop 增加线程中断与持久化 `cancel_requested` 双重门禁，在每轮 LLM 前、LLM 后和 Tool 边界协作式停止
5. 真实运行中取消验证 Loop 在第 0 次迭代以 `cancelled` 停止，LLM/Tool 计数为 0，Task 为 `cancelled`，且事件链最终为 `run.cancelled`
6. 取消不进入失败重试；普通 Tool 失败仍作为有界 Observation，审批恢复仍只重放挂起 Tool。默认 Feature Flag 和百分比灰度保持关闭
7. 下一切片收敛 M5 灰度验收指标、回退开关与运维 Runbook，再决定是否完成 M5 当前范围

第八个纵向切片说明：

1. 新增优先级最高的 `CORE_INTERACTIVE_LOOP_KILL_SWITCH`，熔断时新 Run 不再进入动态循环
2. 灰度路由记录 `selected / fallback + reason` 低基数指标，支持区分总开关、熔断、白名单和百分比结果
3. Loop 停止记录原因计数及迭代、LLM、Tool、Token、计价金额分布，不将 Agent、用户、Tool 或参数写入 Tag
4. Actuator 增加需认证的 `metrics` 端点，用于发布观察，不改变健康检查公开边界
5. Runbook 冻结逐批灰度命令、六类验收门禁、Runtime Event SQL 审计、紧急熔断和恢复原则
6. M5 当前范围完成；默认 Feature Flag 和百分比仍关闭，不自动扩大生产灰度

## 5.8 阶段 7：Runtime SDK 与宿主集成

当前进度：七个纵向切片已完成，阶段 7 当前范围完成。TypeScript Runtime Client、Java Runtime Client、独立 Java 宿主示例、Contract Test 和隔离双服务 E2E 已形成发布闭环。

目标：让前端和其他业务系统不依赖内部 Controller 细节。

首批 SDK：

1. TypeScript SDK
2. Java SDK

首批能力：

1. 创建或查询 Thread
2. 创建 Turn / Run
3. 订阅 Runtime Event
4. 取消或中断 Run
5. 提交审批结果
6. 恢复等待或失败任务
7. 查询产物
8. 发起 Replay

本阶段不承诺与 Codex app-server 协议完全兼容；优先保证当前平台语义稳定，后续再评估适配层。

验收标准：

1. `frontend-web` 可通过 TypeScript SDK 完成会话主链
2. 独立 Java 示例应用可创建 Run、订阅事件并处理审批
3. SDK 对断线续传、错误码和幂等键有统一处理

第一个纵向切片说明：

1. 新增无第三方依赖的 `RuntimeClient`，统一 `baseUrl`、动态 JWT、超时和可替换 `fetcher`
2. 首批实现 `createThread / getThread / createRun / getRun / cancelRun`，屏蔽当前 Conversation Controller 路径
3. `createRun` 支持显式幂等键和优先级，未传入幂等键时由 SDK 生成；`runCode` 统一 URL 编码
4. 新增 `RuntimeSdkError`，冻结配置、参数、HTTP、业务码、非法响应、超时和网络错误
5. 现有会话 API 保留公共函数签名，Thread 和 Run 的创建、查询、取消已委托 SDK，避免页面一次性重构
6. Playwright 覆盖路径、JWT、自动幂等键、URL 编码、401 回调和超时；Core/Registry 同步允许本地 E2E Origin `127.0.0.1:5179`
7. 第一切片完成后，Runtime Event SSE 与断线续传列为第二切片范围

第二个纵向切片说明：

1. 新增 `RuntimeClient.streamEvents`，统一 Runtime Event 路径、动态 JWT、`runCode` URL 编码和可替换 `fetcher`
2. SDK 使用 `afterSequence + Last-Event-ID` 双游标续传，并按 `sequenceNo` 幂等去重；非终态断连自动从最后游标重连
3. 消息增量、进度、审批、产物和 Run 状态投影迁入 SDK，原会话事件入口降为兼容适配层，灰度与旧 SSE 回退保持不变
4. 流式消费统一抛出 `RuntimeSdkError`，补充 `STREAM_NETWORK_ERROR / STREAM_TIMEOUT / STREAM_CLOSED / ABORTED` 稳定错误码，401 继续触发宿主认证回调
5. Playwright 覆盖 SDK 直接订阅、JWT、URL 编码、初始游标、断线续传、重复事件忽略、主动取消和 401；下一切片接入 Approval 与任务恢复 API

第三个纵向切片说明：

1. 新增 `resumeTask / approveTask / rejectTask / revokeApproval / signalWait`，统一失败恢复、审批处理和持久化等待唤醒语义
2. Thread、Task、Approval ID 统一做正整数校验，`eventKey` 统一 URL 编码，拒绝操作支持 `replanAfterReject`
3. Approval 和失败恢复保持现有 10 分钟长请求窗口，避免 SDK 默认 60 秒超时导致长任务恢复回归
4. 会话 API 原公共函数签名不变，审批弹窗继续在对话窗口关闭后等待执行结果，并通过 SDK 沿原 Checkpoint 恢复
5. Playwright 覆盖全部恢复路径和请求体，并回归 Runtime Event；下一切片接入 Artifact 下载和 Replay API

第四个纵向切片说明：

1. 新增 `downloadArtifact`，结构化接收 Thread、Task 和 Path，返回 Blob、UTF-8 文件名、Content-Type 和大小，不把浏览器下载行为耦合进 SDK
2. Artifact 路径通过 `URLSearchParams` 编码，服务端文件名优先按 RFC 5987 `filename*` 解码，兼容中文、空格和括号
3. 新增 `listReplays / getReplay / createSimulationReplay` 及稳定类型，统一分页、编码、事件范围校验和 Simulation 长请求窗口
4. 会话 Artifact 下载与 Replay 管理 API 保留原公共签名并委托 SDK，页面权限、下载交互、Manifest、Event Diff 和 Artifact 校验展示不变
5. Playwright 覆盖 SDK Artifact/Replay 契约并回归 Replay 页面；TypeScript SDK 当前计划能力已完成，下一切片建立 Java SDK Thread/Run 基座

第五个纵向切片说明：

1. 新增独立 Maven 模块 `agent-runtime-sdk-java`，不依赖 Spring Boot，仅使用 Jackson 和 Java 17 原生 `HttpClient`
2. 实现 `createThread / getThread / createRun / getRun / cancelRun`，冻结基础请求/响应模型，并统一 Run Code URL 编码
3. Java Client 每次动态读取 JWT，Run 自动生成 UUID 幂等键并支持显式优先级，默认请求超时 60 秒
4. 新增 `RuntimeHttpTransport` 扩展点和默认 `JdkRuntimeHttpTransport`，允许业务系统替换企业网关或测试传输
5. 新增 `RuntimeSdkException`，统一配置、参数、HTTP、业务、非法响应、超时、线程中断和网络错误；下一切片实现 Java Runtime Event、Approval、Recovery、Artifact 和 Replay

第六个纵向切片说明：

1. Java `RuntimeHttpTransport` 新增流式响应扩展，默认 JDK Transport 支持 SSE 和二进制 InputStream，原 JSON Transport 入口保持兼容
2. 新增阻塞式 `streamEvents`，支持 `afterSequence + Last-Event-ID`、`sequenceNo` 去重、正常断连续传、总超时、连续空连接保护和线程中断
3. Runtime Event Listener 统一投影消息增量、进度、审批、产物与 Run 状态，终态和等待态返回最后事件序号
4. 新增 Approval 通过/驳回/撤回、失败恢复、等待事件唤醒、Artifact 下载和 Replay 分页/详情/Simulation API
5. Artifact 使用 `byte[]` 返回并解析 RFC 5987 UTF-8 文件名，Replay 与 Approval 保持长请求窗口，ID、分页和事件范围统一校验
6. 单元测试覆盖事件断线续传、重复去重、JWT/游标、审批恢复、中文 Artifact 和 Replay；下一切片增加独立 Java 示例应用、契约测试与阶段发布验收

第七个纵向切片说明：

1. 新增独立 `agent-runtime-sdk-java-example` Maven 模块和可执行 Fat Jar，演示 Thread、异步 Run、SSE、审批与续传主链
2. 示例 JWT 优先通过 `AGENT_RUNTIME_TOKEN` 注入，不输出 Token、Tool 参数或原始事件 Payload；自动审批设置安全轮次上限
3. 新增真实 JDK HTTP/SSE Contract Test，冻结 JWT、幂等键、优先级、Path 编码、`afterSequence + Last-Event-ID` 和审批恢复请求
4. 平台 E2E 在隔离 pgvector、真实双后端和 Mock LLM 环境运行示例 Jar，校验 `completed`、Task 关联及最终事件序号
5. E2E 预算熔断策略在用例完成后停用，避免测试状态污染后续 SDK 验收；完整 Maven 示例模块测试和平台 E2E 均通过
6. 阶段 7 当前范围完成，M5 双执行模式与 SDK 里程碑完成；后续工作转入真实集群、供应链和容量/SLO 等生产化验证

预计工作量：5 至 7 个开发日

---

## 5.9 阶段 8：多模态输入与图片能力

目标：把图片作为可持久化、可授权、可重放的运行时输入资源接入现有 AgentLoop，而不是在会话服务中硬编码 OCR 分支。

实施顺序：

1. M6-1：建立 `conversation_attachments`、`AttachmentStorage` SPI、本地存储实现，以及受权上传/下载接口
2. M6-2：消息引用附件，前端支持上传、预览和历史消息展示
3. M6-3：附件进入 `ExecutionContext`，异步 Run 按消息与附件引用恢复现场
4. M6-4：建立供应商无关的 `LlmContentPart`，由 Provider Adapter 转换视觉模型协议
5. M6-5：注册 `IMAGE_OCR / IMAGE_LAYOUT_ANALYZE` 等插件化 Tool
6. M6-6：完善能力路由、Runtime Event、Replay、完整性验证和端到端测试

边界与约束：

1. 文件正文不写入消息、Run、Event 或 PostgreSQL JSONB，只保存 `attachmentId / storageKey / sha256` 等引用
2. `registry-center` 管理模型输入模态和图片 Tool 定义；`agent-core` 管理附件实例和运行时执行
3. 视觉模型直接理解与 OCR Tool 是两条可路由能力，不强制所有图片先 OCR
4. 下载必须校验会话归属和当前用户，响应文件名使用 UTF-8 编码
5. 每个纵向切片独立提交、验证和发布，保留纯文本请求兼容性

当前进度：M6-1 至 M6-6 已完成。附件输入现场已进入 `ExecutionContext`；新增供应商无关的 `LlmContentPart`，图片正文只在模型调用期读取并校验，OpenAI-compatible Adapter 负责临时转换 Data URL。模型通过 `metadata.inputModalities` 声明图片输入能力；独立 `image-understanding-plugin` 以 `attachmentId` 安全解析当前消息附件，提供本地 `IMAGE_LAYOUT_ANALYZE` 和可配置 OpenAI-compatible Vision Provider 的 `IMAGE_OCR`。最终回复阶段按“视觉模型原图直读 -> 非视觉模型使用 Agent 已授权 OCR Tool -> 明确能力缺口”确定性路由；`input.attachment.resolved / analyzed` 只记录附件引用与 SHA-256，Simulation Replay 将其作为录制事实注入并校验。隔离平台 E2E 已验证双插件 Jar、multipart 上传、异步 Run、多模态 Provider 协议、附件事件无 Base64、18 个 Task 与 Java SDK 主链。

## 6. 里程碑和依赖关系

| 里程碑 | 包含阶段 | 可交付价值 | 前置依赖 |
| --- | --- | --- | --- |
| M1 统一运行协议 | 阶段 0、1 | 所有执行、审批和产物统一事件化 | 无 |
| M2 可验证重放 | 阶段 2 | 无副作用模拟重放和结构化 Diff | M1 |
| M3 插件化内核 | 阶段 3 | 内核与存储、事件、步骤实现解耦 | M1 |
| M4 能力插件治理 | 阶段 4、5 | 完整插件规范及能力级审批 | M3 |
| M5 双模式与 SDK | 阶段 6、7 | 动态 Tool Loop 和业务系统嵌入 | M1、M3、M4 |
| M6 多模态输入 | 阶段 8 | 图片持久化输入、视觉模型与 OCR Tool 双路由、附件事实重放 | M1、M2、M4、M5 |

推荐顺序：

```text
阶段 0 -> 阶段 1 -> 阶段 2
                 -> 阶段 3 -> 阶段 4 -> 阶段 5 -> 阶段 6 -> 阶段 7 -> 阶段 8
```

阶段 2 与阶段 3 可在事件协议稳定后并行，但不得同时修改同一批 Orchestrator 和事件发布文件。

---

## 7. 测试与发布门禁

### 7.1 必须新增的测试

1. Runtime Event Schema Contract Test
2. Event 顺序、重复写入和断线续传测试
3. 旧 SSE 与新 Runtime Event 兼容测试
4. Approval Request / Resolve / Resume 状态机测试
5. Prompt Replay 与 Simulation Replay 隔离测试
6. Replay 禁止外部副作用测试
7. Checkpoint Recovery 与 Replay 语义区分测试
8. Plugin Manifest、版本、依赖和冲突测试
9. Capability Policy 参数级风险决策测试
10. `workflow_plan` 与 `interactive_loop` 预算和停止条件测试
11. Worker 租约失效后的事件连续性测试
12. 跨租户 Event、Replay 和 Plugin 资源隔离测试

### 7.2 E2E 验收场景

1. 普通流式问答
2. 单 Tool 和多 Tool 调用
3. PDF 生成与下载产物
4. 高风险 Tool 会话内审批
5. 审批通过后的 Checkpoint 恢复
6. 审批拒绝和 Replan
7. Durable Wait 跨进程恢复
8. Tool 超时、熔断和 Fallback
9. Simulation Replay
10. 插件未安装、版本不兼容和安全下线
11. Interactive Loop 达到预算上限
12. 旧前端兼容模式

### 7.3 发布策略

1. 新协议初期只做双写，不切换读取
2. 验证事件完整性后，管理后台先切换新协议
3. 会话窗口按 Agent 或 Project 灰度切换
4. Replay 先对管理员开放，只允许 `prompt_replay / simulation_replay`
5. 新内核与旧内核执行结果并行对比，不同时对用户返回两份结果
6. 每个里程碑必须具备独立 Feature Flag 和回滚路径

---

## 8. 风险与控制措施

### 8.1 双写不一致

风险：Event Store、Task/Step 和旧 SSE 状态不一致。

控制：

1. 业务状态提交和 Outbox Event 写入使用同一事务
2. SSE 从持久化 Event Store 读取，不依赖本机内存
3. 建立 Projection 延迟和失败告警

### 8.2 事件数量增长

风险：Delta 和 Step 事件快速增长。

控制：

1. Delta 合并和批量写入
2. Payload 大字段使用快照引用或 Artifact 存储
3. 按 Tenant 和时间设置归档保留策略

### 8.3 Replay 重复副作用

风险：重放导致重复写文件、调用外部接口或修改业务记录。

控制：

1. 默认只开放 Simulation Replay
2. Tool Capability 和 Side Effect 决定是否允许真实重放
3. 强制使用幂等键、补偿或人工审批

### 8.4 内核重构回归

风险：审批、Recovery、产物和 Workflow 出现行为变化。

控制：

1. Adapter 优先，不直接重写业务逻辑
2. 新旧内核影子对比
3. 每拆一个职责就补充 Contract Test
4. 不在同一批次同时改协议、Planner 和 Workflow 语义

### 8.5 插件安全

风险：第三方 Jar 在 Core JVM 内拥有过大权限。

控制：

1. 第一阶段只允许受信任签名插件
2. Manifest 声明权限，Registry 审核后发布
3. 高风险 Provider 优先运行在独立进程或 MCP Server
4. 后续增加签名、SBOM、漏洞扫描和隔离 ClassLoader

---

## 9. 第一批实施任务

第一批只实施 M1，不同时进入插件或动态 Agent Loop：

1. 编写 Runtime Protocol 规范
2. 编写统一状态机文档
3. 完成事件表、索引、RLS 和 Outbox 设计
4. 定义 `RuntimeEventEnvelope` 和 `RuntimeEventPublisher`
5. 将现有 `progress / reset / delta` 接入统一发布器
6. 增加 Tool、Approval、Artifact 和 Run 终态事件
7. 新增 Runtime Event SSE API
8. 前端新增事件消费适配器，保持旧页面交互不变
9. 补齐 Contract Test、断线续传测试和核心 E2E
10. 灰度发布并核对新旧事件一致性

M1 完成审计：上述 10 项均已完成。Runtime Protocol、统一状态机、事件存储隔离与 Outbox 设计、后端生命周期事件、统一/旧 SSE、前端消费器、协议契约与浏览器续传测试、Agent 灰度开关、回滚手册和本地 Docker 发布验证均已落地；旧 SSE 按计划继续保留，M2 Replay 未提前实施。

第一批明确不做：

1. 不实现 JVM 插件热卸载
2. 不开放 `execution_replay`
3. 不删除旧 SSE 和旧 Controller
4. 不替换现有 Workflow
5. 不引入新微服务
6. 不迁移现有历史 Trace 数据

---

## 10. 完成定义

专项改造完成需同时满足：

1. 所有 Run 可通过统一事件完整还原用户可见执行过程
2. 审批、产物、Tool 和错误不依赖聊天文案表达状态
3. Simulation Replay 不产生外部副作用并能输出结构化 Diff
4. 执行内核可以替换事件、Checkpoint 和 Step Handler 实现
5. Tool 风险由 Capability Policy 决定，Planner 不能绕过
6. Workflow Plan 和 Interactive Loop 共用同一 Tool、审批、事件和治理体系
7. 领域插件不向 `agent-core` 回流业务规则
8. Java / TypeScript SDK 可完成 Run 生命周期管理
9. 原有同步、异步、审批、恢复、等待、产物和 E2E 主链全部通过
10. 数据库设计、接口文档、运行手册和项目计划同步更新
