# Agent 面试题与当前项目实践

> 更新时间：2026-07-28
> 适用方向：Java 后端 / Agent 平台 / LLM 应用工程 / AI 应用架构

## 1. 结论

当前项目已经具备一个通用 Agent 平台的主要工程闭环，不只是“接入 LLM 的聊天应用”。面试时最适合围绕以下主线介绍：

```text
用户输入
  -> 会话与运行队列
  -> ExecutionContext 构建
  -> Decision 资源选择
  -> Planner 任务拆解与计划校验
  -> Orchestrator 执行 Tool / Workflow / Approval / Handoff 等步骤
  -> Observation 与 Replan
  -> Guardrail、结果可信度校验
  -> 响应、Memory、Trace、Cost 回写
```

项目的主要亮点是：

1. 将 Agent 的资源定义与运行时执行拆成 `registry-center`、`agent-core` 两个独立服务。
2. 将 `Decision` 与 `Planner` 拆开，先选资源，再生成带依赖和失败策略的执行计划。
3. Tool 同时支持内置 SPI、外部 HTTP 和 MCP，不把能力硬编码在单个 Agent 中。
4. 对 Approval、Recovery、Checkpoint、Durable Wait、Tracing、Eval、Cost、Sandbox、租户隔离做了工程化实现。
5. 当前仍处于生产前验证阶段，真实 Embedding、真实供应商认证矩阵、Kubernetes 压测、KMS/Vault 和供应链安全仍需完善。

## 2. 当前项目能力地图

| 能力 | 当前实现 | 主要代码位置 | 面试表述 |
|---|---|---|---|
| 会话与执行上下文 | 聚合用户、会话、Agent、Prompt、模型、Memory、Tool、Workflow、Delegate 等运行时信息 | `ExecutionContext`、`ExecutionContextBuilder` | 已实现，不把 Context 与 Memory 混为一个字段 |
| Decision | 支持 `llm_first`、`rule_first`、`rules_only`，LLM 失败可按策略降级 | `ExecutionDecisionService`、`AgentExecutionPolicyResolver` | 已实现规则与 LLM 混合路由 |
| Planner | LLM/规则生成富计划，支持计划校验、质量评分、版本、Diff、自动/人工 Replan | `ExecutionPlannerService`、`PlanValidatorService`、`PlanQualityService` | 已实现 Plan-and-Execute 主链 |
| 执行编排 | 支持 `tool`、`workflow`、`reply`、`condition`、`approval`、`handoff`、`reflection`、`clarify`、`judge`、`wait`、`memory_write`、`parallel`、`join` | `ExecutionOrchestratorService`、`ExecutionStepType`、各 Step Handler | 已实现多类型步骤，不只支持 Tool |
| Tool | 内置 Tool、外部 HTTP、MCP；Schema 校验、生命周期路由、熔断、Fallback、健康检查、输入治理 | `ToolRouterService`、`ToolProviderAdapter`、`ToolSchemaValidationService` | 已实现可插拔 Tool Runtime |
| MCP | 支持 Streamable HTTP、stdio、legacy SSE，执行 `initialize`、`tools/list`、`tools/call` | `McpToolProviderAdapter`、`McpStdioTransport`、`McpLegacySseTransport` | 已实现 MCP Client 侧核心链路 |
| Workflow | 注册中心保存定义，运行时执行图；支持条件、审批、恢复、节点观测和结果组装 | `WorkflowExecutorService`、`WorkflowGraphNavigator` | 已实现定义与实例边界分离 |
| RAG / Knowledge | PostgreSQL + pgvector，关键词和向量混合检索，权限过滤、引用、Recall@K、MRR | `KnowledgeRetrievalService`、`KnowledgeRuntimeRepository` | 工程链路已实现，Embedding 质量仍是待完善项 |
| Memory | 会话记忆、用户记忆、任务快照、槽位状态；选择、注入、更新、治理、压缩分层 | `memory` 模块、`ContextCompactionService` | 已实现分层 Memory 与 Context 管理 |
| Multi-Agent | Planner 生成 `handoff`，执行目标 Agent 委派；有委派树与治理规则 | `AgentDelegationService`、`DelegationGovernanceService` | 已实现受控 Delegation，不是自由群聊 |
| Human-in-the-loop | 高风险步骤审批、超时、撤回、驳回后 Replan、审批后恢复 | `ApprovalStepService`、`ApprovalRecoveryStateMachineService` | 已实现可恢复审批状态机 |
| Recovery | Tool 重试、失败分类、计划恢复、Workflow 节点恢复、Checkpoint、Fork | `ExecutionFailureClassifierService`、`ExecutionCheckpointService` | 已实现分层恢复；不是所有错误都盲目重试 |
| 长任务 | PostgreSQL 持久队列、租约、心跳、能力匹配、公平调度、背压、幂等键、取消 | `AgentRunQueueRepository`、`AgentRunWorker` | 已实现数据库型分布式运行队列 |
| 安全 | JWT、RBAC、RLS、Guardrail、结果可信度、Sandbox、HTTP 出站限制、Trace 脱敏 | `security`、`GuardrailPolicyService`、`SandboxPolicyValidator` | 已实现纵深防御基础链路 |
| 可观测性 | Task Trace、拓扑、回放、Micrometer/OpenTelemetry 指标、慢步骤分析 | `TaskTraceService`、`TraceInvestigationService` | 已实现 Agent/LLM/Tool 级观测 |
| Eval | Suite、Case、版本、Baseline、Run Compare、规则判分、LLM Judge、Release Gate | `EvalService`、`EvalLlmJudgeService`、`EvalReleaseGateService` | 已实现离线回归与发布门禁基础能力 |
| 成本治理 | LLM/Tool/MCP/Sandbox/Delegation 统一计量，价格历史、预算、告警和调用前熔断 | `CostGovernanceService` | 已实现用量归因和预算硬限制 |

## 3. 高频面试题、参考解答与项目运用

### 3.1 什么是 AI Agent？与普通 Chatbot 有什么区别？

**参考解答：**

Agent 是以目标为导向、能够根据上下文进行决策、调用外部工具、观察执行结果并继续行动，直到达到终止条件的系统。基础组成通常是 `Model + Instructions + Tools + Run Loop + Guardrails`。

普通 Chatbot 主要完成“输入 -> 模型 -> 文本输出”；Agent 的关键区别是模型参与控制执行流程，并能对外部环境产生动作。只有一次 LLM 调用、没有工具和状态循环的应用，通常不应称为 Agent。

**当前项目运用：**

项目通过 `ExecutionDecisionService` 选择资源，通过 `ExecutionPlannerService` 生成计划，再由 `ExecutionOrchestratorService` 执行并观察结果。最终回复只是运行结果的一部分，不是整个 Agent。

### 3.2 Agent、Workflow 应该如何选择？

**参考解答：**

- 步骤固定、合规要求高、分支明确时，优先 Workflow。
- 任务目标明确但过程存在歧义、需要动态选工具或根据结果调整时，使用 Agent。
- 企业系统通常采用混合模式：Agent 负责理解、路由和规划，Workflow 负责高确定性执行。

不能把所有流程都交给 LLM，否则成本、延迟、可重复性和审计性会变差；也不能把所有流程写死，否则遇到非结构化输入和长尾情况会迅速膨胀为规则地狱。

**当前项目运用：**

`ExecutionDecision` 可以选择 Tool 或 Workflow；Planner 可以把 `condition`、`approval`、`handoff` 编入计划。高风险确定性流程放注册中心 Workflow，动态任务由 Planner 组织。

### 3.3 请介绍当前项目一次完整的 Agent 执行流程。

**参考解答：**

1. `registry-center` 签发 JWT，`agent-core` 本地验签并解析租户、角色、项目范围。
2. 同步请求直接执行，异步请求进入 `agent_runs` 持久队列，由 Worker 通过租约领取。
3. `ExecutionContextBuilder` 汇总 Agent、Prompt、阶段模型、Memory、Tool、Workflow、会话和任务现场。
4. Decision 只做资源选择，避免同时承担顺序规划。
5. Planner 生成带 `stepId`、依赖、输入、预期输出和失败策略的计划，服务端再做白名单和结构校验。
6. Orchestrator 按依赖执行步骤，执行前做 Guardrail、Schema、预算和审批检查。
7. 每步结果写入 `task_steps` 和 Trace，Observation 判断继续、停止或 Replan。
8. 最终输出经过 Result Trust 和 Output Guardrail，再更新消息、Memory、Task 状态、成本与指标。

### 3.4 为什么要把 Decision 和 Planner 分开？

**参考解答：**

Decision 解决“本次允许使用哪些资源”，Planner 解决“这些资源以什么顺序完成目标”。拆分后的收益是：

1. 缩小 Planner 的资源空间，减少幻觉引用不存在 Tool 的概率。
2. Decision 可使用低成本模型或规则快速路径，Planner 只在复杂任务中调用更强模型。
3. 权限和资源白名单更容易在两个边界上校验。
4. 决策错误与计划错误可以分别评测、追踪和降级。

**当前项目运用：**

`ExecutionDecision` 返回 `selectedTools / selectedWorkflows`；`PlanValidatorService` 校验 Planner 只能引用已选择且当前用户有权使用的资源。

### 3.5 什么是 ReAct？当前项目是不是 ReAct？

**参考解答：**

ReAct 的核心是交替执行 `Reasoning -> Action -> Observation`，让模型根据工具返回继续修正下一步行动。它适合路径难以提前完全确定的任务，但必须设置最大步数、预算、终止条件和敏感动作审批，防止无限循环和过度执行。

当前项目更准确的描述是“`Plan-and-Execute + Step Observation + Replan`”，而不是完全由模型逐轮自由生成 Thought/Action 的原始 ReAct。Planner 先生成结构化计划，每步完成后由 `ObservationDecisionService` 决定继续、停止或重规划。这种方式更适合 Java 企业平台的审计和恢复要求。

### 3.6 如何防止 LLM 生成非法计划或不存在的 Tool？

**参考解答：**

不能只靠 Prompt，应使用服务端强校验：

1. 模型输出使用结构化 JSON。
2. 步骤类型使用枚举白名单。
3. Tool、Workflow、Delegate 必须来自当前 ExecutionContext 的授权快照。
4. 校验 `stepId` 唯一性、依赖存在性、无环性、必填输入和输出契约。
5. 不合法时有限重试或回退规则计划，不能直接执行。

**当前项目运用：**

`ExecutionStepType` 维护可执行类型，`PlanValidatorService`、`StepDataContractService`、`StepOutputContractService` 分别校验计划、步骤数据和输出。

### 3.7 Function Calling、Tool 和 MCP 有什么区别？

**参考解答：**

- Function Calling 是模型输出结构化函数名和参数的能力，不负责函数真实执行。
- Tool 是 Agent Runtime 中可被选择和执行的能力抽象，可以是 Java 方法、HTTP API、脚本或另一个 Agent。
- MCP 是连接 AI 应用与外部能力的标准协议，定义初始化、能力发现、Tool/Resource/Prompt 等原语和 JSON-RPC 消息，不等于某一家模型的 Function Calling。

**当前项目运用：**

项目统一通过 `ToolProviderAdapter` 屏蔽 `internal`、`external_http`、`mcp` 差异；MCP Runtime 支持 `initialize`、`notifications/initialized`、`tools/list`、`tools/call` 及多种 Transport。

### 3.8 Tool 设计最重要的工程点有哪些？

**参考解答：**

1. Tool 名称、描述和适用边界清晰，避免语义重叠。
2. 输入输出使用 JSON Schema，参数尽量小而明确。
3. 读操作与写操作分级，声明副作用、幂等性和审批要求。
4. 设置超时、限流、重试、熔断、Fallback 和健康检查。
5. Tool 返回结构化事实和错误码，不把异常堆栈直接塞给模型。
6. 执行前再次校验权限，不能认为“模型选择了就有权限”。

**当前项目运用：**

`ToolSchemaValidationService` 做契约校验，`ToolSideEffectService` 管理副作用确认，`ToolCircuitBreakerService` 处理熔断，`ToolSelectionService` 根据健康度和路由策略选实例，`ToolTraceHook` 记录调用链。

### 3.9 Tool 调用失败时如何重试？

**参考解答：**

先分类，再决定是否重试：

- 网络超时、连接失败、429、部分 5xx 一般可重试。
- 参数错误、权限不足、Schema 不匹配一般不可重试。
- 写操作只有具备幂等键、去重或补偿机制时才能自动重试。
- 使用指数退避加抖动，并设置最大次数和总预算。

**当前项目运用：**

项目使用 `ToolExecutionException.retryable` 和 `ExecutionFailureClassifierService` 分类；普通 Tool 步骤自动重试上限较小，Run Worker 使用指数退避与抖动。写操作由副作用治理和 Approval 保护，避免把“多试几次”变成重复扣款或重复写入。

### 3.10 RAG 的完整链路是什么？怎么评估？

**参考解答：**

典型链路是：文档解析 -> 清洗 -> Chunk -> Embedding -> 向量/关键词索引 -> 权限过滤 -> Query 改写 -> 检索 -> Rerank -> 上下文组装 -> 带引用生成。

评估至少分两层：

- 检索层：Recall@K、MRR、NDCG、命中率、检索延迟。
- 生成层：Faithfulness、Answer Relevance、引用正确率、完整性和拒答正确率。

**当前项目运用与边界：**

项目已实现 pgvector、关键词/向量混合检索、知识库权限过滤、引用、Recall@K 和 MRR。但是 `KnowledgeEmbeddingService` 当前使用 384 维特征哈希作为无外部 Embedding 模型时的降级方案，不应在面试中声称已经达到真实语义向量模型的召回效果。下一步应接入注册中心的 Embedding Model，再补 Reranker 与生成层评测。

### 3.11 Context 和 Memory 有什么区别？

**参考解答：**

Context 是本次模型调用可见的运行时输入；Memory 是可以跨步骤或跨会话保存、选择并重新注入 Context 的信息。Memory 不是把所有历史消息永久拼接，而是要经过提取、分类、置信度、时效、权限、冲突处理和遗忘策略。

可以按范围拆为：

- Working Memory：当前任务中间结果。
- Conversation Memory：本会话摘要和槽位。
- User Memory：跨会话偏好、事实、目标。
- Episodic/Task Memory：历史任务和成功计划经验。

**当前项目运用：**

`MemoryItemCollectorService` 收集候选，`MemorySelectorService` 按执行场景选择，`MemoryInjectionPolicyService` 组装上下文，`MemoryUpdaterService` 在执行后更新。`ExecutionContext` 只承载已解析的现场，不直接负责记忆写入。

### 3.12 长对话超过 Context Window 怎么办？

**参考解答：**

不能简单截断最早消息。较稳妥的策略是：

1. 为 system、tools、history、retrieval、output 分配 Token Budget。
2. 保留最近原文窗口和关键确认事实。
3. 对旧消息生成滚动摘要，并分短期、中期、长期层次。
4. 对摘要记录来源、版本和压缩指标，必要时可回放原始记录。
5. 不同阶段使用不同上下文，例如 Decision 不需要完整回答风格 Prompt。

**当前项目运用：**

`PromptContextBudgetService` 按阶段分配预算，`ContextCompactionService` 负责压缩与滚动摘要，`ExecutionContext` 保护已确认槽位、事实、偏好、约束和目标，并把压缩元数据写入 Trace。

### 3.13 Memory 有什么安全风险？

**参考解答：**

Memory 可能把一次恶意 Prompt Injection 变成长期污染。防护要点是：

1. 不把 Tool 输出或外部文档中的指令直接当系统指令保存。
2. 记忆写入做来源、类型、置信度和敏感数据检查。
3. 用户事实与模型推断分开，低置信推断需要确认。
4. 注入时按租户、用户、Agent 和任务范围过滤。
5. 支持查看、修正、删除和审计。

**当前项目运用：**

项目已有 `MemoryGovernanceService`、会话记忆策略、槽位冲突处理和委派记忆治理；仍应继续补强间接 Prompt Injection 检测和用户可见的记忆纠错入口。

### 3.14 如何实现 Human-in-the-loop？

**参考解答：**

审批不能只是前端弹窗，而应是可持久化、可恢复的状态机：

1. 高风险步骤执行前创建 Approval，Task 进入 `waiting_approval`。
2. 保存当前计划版本、下一步骤、已执行结果和幂等信息。
3. 审批人按 RBAC 处理通过、驳回、撤回或超时。
4. 通过后从 Checkpoint 恢复，驳回可终止或触发 Replan。
5. 所有动作进入审计和 Trace。

**当前项目运用：**

`ApprovalStepService` 负责挂起，`ApprovalRecoveryStateMachineService` 校验状态流转，`RecoveryValidationService` 校验恢复现场，前端有 Approval Workbench。

### 3.15 Agent 如何实现可恢复的长任务？

**参考解答：**

核心是把“线程栈”转换为“持久化状态”：计划版本、当前步骤、步骤输入输出、外部副作用、等待事件、重试次数和 Checkpoint 都要落库。恢复时从最近安全 Checkpoint 继续，而不是重放所有步骤。

需要特别处理：

- 至少一次执行带来的重复副作用。
- 版本变更导致旧计划和新资源不一致。
- 等待期间 Token、权限或配置发生变化。
- 多 Worker 同时恢复同一任务。

**当前项目运用：**

项目使用 `ExecutionCheckpointService`、`DurableWaitService`、任务计划版本和步骤快照；队列通过 lease 防止长期占锁，通过幂等键和副作用审计降低重复执行风险。

### 3.16 什么情况下使用 Multi-Agent？

**参考解答：**

单 Agent 加 Tool 能解决时优先单 Agent。只有出现清晰的专业边界、上下文隔离、权限隔离、并行收益或独立评测需求时，才引入 Multi-Agent。

常见模式：

- Manager：主 Agent 统一面向用户，把子 Agent 当 Tool。
- Handoff：当前 Agent 把控制权交给另一个 Agent。
- Parallel Specialists：多个专业 Agent 并行产出，由 Join/Judge 汇总。

主要风险包括上下文膨胀、递归委派、权限扩散、成本失控、结果冲突和责任不清。

**当前项目运用：**

当前采用受控 Handoff/Manager 思路，Planner 只能选择授权的 Delegate；`DelegationGovernanceService` 限制深度、次数和循环，父任务保存委派树和审计链。

### 3.17 如何防止 Agent 无限循环或反复 Replan？

**参考解答：**

应同时限制：最大步骤数、最大 Replan 次数、LLM 调用次数、总 Token、总费用、执行时长和相同失败指纹次数。终止条件必须在 Runtime 中实现，不能只写在 Prompt 中。

**当前项目运用：**

项目有 `LlmCallBudget`、计划质量校验、失败指纹熔断、成本预算、队列尝试次数和委派治理。Observation 只能返回受支持的后续动作，不能无限生成新类型操作。

### 3.18 如何防御 Prompt Injection 和 Tool Misuse？

**参考解答：**

Prompt Injection 无法仅靠一句“忽略恶意指令”彻底解决，应采用纵深防御：

1. 区分系统指令、用户输入、检索文档和 Tool 输出的信任等级。
2. Tool 使用最小权限，读写分离，高风险动作审批。
3. 结构化输入输出并做 Schema 校验。
4. 外部 URL、文件路径、命令和 SQL 使用允许列表。
5. 输出做敏感信息脱敏和不安全内容拦截。
6. 记录完整审计，但 Trace 中不能泄露 Token、密钥和敏感正文。

**当前项目运用：**

项目在执行前使用 `GuardrailPolicyService`，最终回复落库前使用 `OutputGuardrailService`，并结合 Tool 权限、Side Effect Approval、Sandbox、`SafeHttpClient`、Trace 脱敏和 JWT/RLS。

### 3.19 为什么还需要 Sandbox？

**参考解答：**

模型生成的代码和命令不能直接在宿主机执行。Sandbox 至少要限制镜像、CPU、内存、进程数、超时、网络、挂载目录、工作目录和输出大小；容器不是绝对安全边界，生产环境还应使用 rootless、seccomp/AppArmor、只读文件系统、镜像签名和漏洞扫描。

**当前项目运用：**

`SandboxPolicyValidator` 在执行前校验策略，`DockerSandboxExecutor` 负责容器执行，`SandboxWorkspaceService` 管理工作区。当前还需要补齐真实集群隔离、镜像签名、SBOM 和 KMS/Vault。

### 3.20 Agent 系统如何做可观测性？

**参考解答：**

至少观察三层：

- Run：成功率、总延迟、停止原因、计划版本、Replan 次数。
- Step/Tool：每步耗时、重试、错误类型、审批等待、外部依赖。
- LLM：模型、Token、首 Token 延迟、总延迟、finish reason、错误码和费用。

Trace 结构通常是 `invoke_agent -> chat / execute_tool / workflow_node / approval / handoff`。Prompt 和 Tool 参数可能含敏感信息，应默认只记录摘要、Hash 或脱敏内容。

**当前项目运用：**

项目将 Task、Step、Approval、Delegation 组织成 Trace 拓扑，并通过 Micrometer/OpenTelemetry 记录 LLM 和 Tool 指标；前端支持慢步骤、拓扑、回放和问题排查。

### 3.21 Agent 应该如何评测？

**参考解答：**

不能只看最终答案，也不能只依赖 LLM-as-a-Judge。建议组合：

1. 确定性 Grader：状态、字段、Tool 调用、引用、正则、JSON Schema。
2. 轨迹 Grader：是否选对 Tool、是否绕路、是否越权、步骤是否合理。
3. 模型 Grader：相关性、完整性、风格等难以规则化的指标。
4. 人工抽检：高风险和模型不确定样本。
5. 线上指标：任务完成率、人工接管率、成本、延迟和投诉。

测试集要版本化，并保留 Baseline；模型、Prompt、Tool 或策略变更都应触发回归。

**当前项目运用：**

`EvalService` 管理 Suite/Case/Version/Run，支持 Baseline、Run Compare、规则判分和 LLM Judge；`EvalReleaseGateService` 负责发布阈值。Knowledge 另有 Recall@K、MRR 检索评测。

### 3.22 如何优化 Agent 的延迟和成本？

**参考解答：**

优先顺序通常是：

1. 简单请求使用规则或直接回复快速路径。
2. 按 `decision/planning/reply/reflection` 分阶段选择模型。
3. 缩小 Tool 清单和上下文，避免把所有 Schema 都发给模型。
4. 无依赖步骤并行，有依赖步骤顺序执行。
5. 缓存稳定 Prompt、检索和 Tool 结果，但写操作不能随意缓存。
6. 设置 Token、调用次数、费用和时长硬预算。
7. 用 Eval 证明小模型替换后仍达到质量阈值。

**当前项目运用：**

项目支持规则优先、阶段模型、上下文压缩、`parallel/join`、LLM 调用硬预算和统一成本账本。`CostGovernanceService.assertAllowed` 在调用前执行预算熔断，而不是事后只做报表。

### 3.23 PostgreSQL 队列如何支持多 Worker？

**参考解答：**

可使用 `FOR UPDATE SKIP LOCKED` 领取任务，用 `lease_until + heartbeat` 处理 Worker 崩溃，用幂等键去重，用优先级和 aging 防止低优先级任务饿死。领取、状态变更和并发容量计算必须在事务内完成。

风险是数据库同时承担业务和队列压力，超大规模下需要评估分库或 Kafka/RabbitMQ，但消息中间件也不能自动解决长任务状态、幂等和恢复问题。

**当前项目运用：**

`AgentRunQueueRepository.claimBatch` 使用 PostgreSQL advisory lock、`SKIP LOCKED`、worker group、capabilities、priority aging 和 lease；`AgentRunWorker` 处理 heartbeat、指数退避、取消和优雅停机。

### 3.24 为什么拆成 `agent-core` 和 `registry-center`？

**参考解答：**

拆分依据不是技术层，而是资源定义与运行实例的生命周期：

- `registry-center` 管“资源是什么、哪个版本可用、谁有权限使用”。
- `agent-core` 管“本次怎么执行、执行到哪里、结果和成本如何”。

这样可以避免注册配置和高频运行表互相耦合，允许独立扩缩容、权限治理和故障隔离。代价是需要处理快照、版本、缓存一致性和跨服务调用失败。

当前项目不允许跨服务直写数据库，通过 HTTP 获取运行时快照，并在 Task 中保存实际使用的资源版本和执行链。

### 3.25 这个项目最有挑战的三个问题是什么？

**参考回答模板：**

1. **不确定模型与确定系统之间的边界。** 我们把模型输出限制为结构化 Decision/Plan，再由服务端做资源白名单、依赖和数据契约校验。
2. **失败恢复与副作用一致性。** 我们把步骤、计划版本、Checkpoint、审批和副作用审计持久化，区分可重试与不可重试错误，恢复时从安全点继续。
3. **上下文、质量和成本的平衡。** 我们按阶段分配 Context Budget，保留关键事实并压缩旧消息，同时通过 Eval Baseline、Trace 和 Cost Ledger 验证优化是否真的有效。

回答时应继续补充一个真实 Bug、一次取舍和最终验证数据；如果没有真实生产数据，不要编造 QPS、成功率或成本下降比例。

### 3.26 当前项目有哪些不足？

**参考解答：**

1. 尚未完成 Kubernetes 多主机压测、容量模型、SLO 和恢复演练。
2. KMS/Vault、镜像签名、SBOM、插件/MCP 供应链验证仍需补齐。
3. 真实 LLM、Embedding、Tool、MCP 供应商认证矩阵仍需扩大。
4. RAG 当前 Embedding 是特征哈希降级实现，还不是生产级语义模型。
5. OpenTelemetry 语义字段和外部 Collector/Backend 的完整生产验证仍可加强。
6. 账单对账、多币种、合规审计和数据归档策略仍属于后续运营能力。

坦诚说明边界通常比宣称“企业级全完成”更可信。

## 4. 60 秒项目介绍模板

> 我做的是一个 Java 17 + Spring Boot 3 的通用 Agent 平台，采用 `agent-core` 和 `registry-center` 双服务架构。注册中心负责 Agent、Prompt、Workflow、Tool、Model、Knowledge 和权限等资源定义，核心服务负责会话、上下文、Decision、Planner、步骤执行、恢复和观测。一次请求会先构建 ExecutionContext，再通过规则或 LLM 选择资源，Planner 生成结构化计划并经过服务端校验，Orchestrator 执行 Tool、Workflow、Approval、Handoff 等步骤，每步都记录 Trace、成本和 Checkpoint，失败时按错误类型重试或 Replan。Tool Runtime 同时支持内置 SPI、外部 HTTP 和 MCP，平台还实现了 Memory、pgvector 混合检索、Docker Sandbox、Human-in-the-loop、Eval Baseline、分布式 Run 队列和多租户隔离。当前已经形成生产前功能闭环，但真实 Embedding、供应商认证矩阵、Kubernetes 压测和供应链安全仍在后续计划中。

## 5. 场景设计题：设计一个“退款 Agent”

可以按以下方式回答：

1. **输入与鉴权：** 校验用户身份、订单归属和租户范围。
2. **Context：** 加载订单、退款政策、历史会话和用户补充信息。
3. **Decision：** 区分政策问答、退款资格查询和真实退款动作。
4. **RAG：** 从已发布退款政策中检索规则并返回引用。
5. **Planner：** 查询订单 -> 校验状态 -> 计算可退金额 -> 风险判断 -> 审批 -> 执行退款 -> 通知用户。
6. **Tool：** 查询 Tool 为只读；退款 Tool 为高风险写操作，要求 JSON Schema、幂等键和明确权限。
7. **Approval：** 超过阈值、特殊商品或模型置信度不足时进入人工审批。
8. **Recovery：** 查询步骤可重试；退款调用超时先根据幂等键查询结果，不能直接重复退款。
9. **Guardrail：** 外部文本不能改变退款政策，Tool 结果不能当系统指令。
10. **Observability：** 保存决策理由、政策版本、审批人、Tool 请求 ID、费用和最终状态。
11. **Eval：** 覆盖正常退款、不可退、重复请求、越权订单、注入攻击、Tool 超时和审批超时。

映射到当前项目时，可使用 `Workflow + Approval + ToolSideEffect + Checkpoint + Trace + Eval Release Gate` 组合完成。

## 6. 面试中不要过度声明的内容

### 6.1 当前没有实际使用 LangChain4j

虽然项目规范将 `LangChain4j` 列为目标技术栈，但当前 Maven 依赖中没有 `langchain4j`。实际实现是自研 `RuntimeLlmGatewayService + LlmProviderAdapter + OpenAiCompatibleLlmProviderAdapter`。

推荐表述：

> 我理解 LangChain4j 的 ChatModel、Tool、RAG 和 Agentic 编排能力，但当前项目为了掌握运行时边界并减少框架锁定，核心链路采用自研适配层；后续可以把 LangChain4j 作为新的 Provider/Embedding Adapter 接入。

### 6.2 当前主要使用 JdbcTemplate，不是 MyBatis-Plus

项目规范列出了 MyBatis-Plus，但当前两个服务主要通过 Spring JDBC/JdbcTemplate 访问数据库。面试时应按实际代码回答，不能把技术规划说成已落地经验。

### 6.3 Redis 不是当前核心运行依赖

规范列出了 Redis，但当前持久队列、Checkpoint、Memory、Trace 和运行状态核心链路主要在 PostgreSQL。可以解释这是为了先保证事务一致性和可恢复性，不应声称已经实现 Redis 分布式缓存体系。

### 6.4 RAG Embedding 仍是降级实现

当前 `KnowledgeEmbeddingService` 使用稳定特征哈希生成 384 维向量，作用是跑通 pgvector 的存储、检索和评测契约。它不能替代真实语义 Embedding 模型。

### 6.5 “生产级”需要数据证明

当前更准确的定位是“企业功能闭环、生产前验证阶段”。除非已经有真实压测报告、SLO、故障演练和供应商回归数据，否则不要宣称大规模生产成熟。

## 7. 建议的复习顺序

1. 先熟练讲清第 3.1、3.2、3.3、3.4、3.7、3.10、3.11、3.16、3.18、3.21。
2. 再结合代码讲 `ExecutionContextBuilder -> ExecutionDecisionService -> ExecutionPlannerService -> ExecutionOrchestratorService`。
3. 准备一个 Tool 失败恢复案例、一个 Approval 场景、一个 RAG 指标案例。
4. 准备当前不足和下一步方案，避免只讲功能清单。
5. 最后用第 5 节的场景题练习 5 到 10 分钟系统设计回答。

## 8. 参考资料

1. [OpenAI: A practical guide to building agents](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/)
2. [Anthropic: Building Effective AI Agents](https://resources.anthropic.com/building-effective-ai-agents)
3. [Anthropic: Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)
4. [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629)
5. [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-11-25)
6. [Model Context Protocol Architecture](https://modelcontextprotocol.io/docs/learn/architecture)
7. [OWASP Top 10 for Agentic Applications 2026](https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/)
8. [OpenTelemetry Generative AI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
