# MVP开发计划

## 1. 结论

本项目当前按 MVP 路线推进，先完成“双服务平台最小可运行闭环”，再进入垂直场景 Agent Demo。

当前总原则：

1. 先做平台底座，不先做复杂场景功能
2. 先完成 `registry-center` 基础能力
3. 再完成 `agent-core` 最小执行闭环
4. 最后做前后端联调和 Demo 验证

计划维护规则：

1. 总计划由 `主控Agent` 负责
2. 每次开发任务结束后，必须检查本计划是否需要更新
3. 若任务完成、拆分、延期、顺序调整，必须及时更新本文件

---

## 2. MVP目标

MVP 阶段目标：

1. 完成双服务项目基础骨架
2. 完成 `registry-center` 认证和基础注册能力
3. 完成 `agent-core` 会话与最小任务执行闭环
4. 完成前端基础页面和联调
5. 跑通一个最小 Demo 链路

---

## 3. 阶段划分

## 3.1 第一阶段：项目骨架与规范

目标：

1. 项目目录初始化
2. 双服务骨架初始化
3. 前端骨架初始化
4. 开发规范落地
5. 多 Agent 协作规范落地

交付物：

1. 项目目录结构
2. `agent-core` 初始骨架
3. `registry-center` 初始骨架
4. `frontend-web` 初始骨架
5. `AGENTS.md`
6. `README.md`
7. 双服务开发约定文档
8. 多 Agent 协作文档

状态：已完成

---

## 3.2 第二阶段：认证与管理入口

目标：

1. 完成 `registry-center` 登录认证模块
2. 完成用户基础模型
3. 完成 `JWT` 签发与校验规则
4. 提供当前登录用户接口
5. 前端完成登录页和登录态接入

交付物：

1. 登录接口
2. 当前用户信息接口
3. 基础认证配置
4. 登录页
5. 登录联调说明

状态：已完成

---

## 3.3 第三阶段：注册中心基础能力

目标：

1. 完成 Agent 注册基础能力
2. 完成 Tool 注册基础能力
3. 完成 Workflow 定义基础能力
4. 完成 Prompt 配置基础能力
5. 完成用户管理基础能力
6. 完成角色管理基础能力
7. 完成用户绑定角色基础能力
8. 完成角色绑定 Agent 基础能力

交付物：

1. Agent 管理接口
2. Tool 管理接口
3. Workflow 定义管理接口
4. Prompt 管理接口
5. 用户管理接口
6. 角色管理接口
7. 用户角色绑定接口
8. 角色 Agent 绑定接口
9. 对应前端管理页面或占位页

状态：已完成

当前完成情况：

1. 已完成用户管理基础能力
2. 已完成角色管理基础能力
3. 已完成用户绑定角色基础能力
4. 已完成角色绑定 Agent 基础能力
5. 已完成 Agent 管理第一批能力（分页、详情、新增、编辑、启停）
6. 已完成 Agent Tool 范围维护能力
7. 已完成 Tool 注册基础能力（分页、详情、新增、编辑、启停）
8. 已完成 Workflow 注册基础能力（分页、详情、新增、编辑、发布、停用）
9. 已完成 Prompt 注册基础能力（分页、详情、新增、编辑、启停）
10. 已完成 `Approval / Recovery` 状态机边界第一批收口，统一 `resume / approve / reject` 入口状态校验
10. 已完成 Tool / Workflow / Prompt 资源分类改造
11. 已将 Agent Tool 范围模式扩展为 `inherit_default`、`inherit_and_append`、`custom`
12. 已优化 Agent Tool 范围维护弹层的交互和页面布局
13. 已增强 Tool 类型与分类在前端页面中的展示
14. 已补资源字典规约
15. 已完成平台字典管理基础能力
16. 已完成 Tool / Workflow / Prompt / Agent 相关字典下拉改造
17. 已完成 Agent 的 Prompt / Workflow 真实绑定选择，并在详情页展示绑定结果
18. 已完成 Prompt 统一模板定位补强，支持 Agent、决策路由、Workflow、Tool、Memory 多类提示词配置
19. 已完成 Agent 决策 Prompt 绑定，以及 Workflow / Tool 的执行 Prompt 与路由信息结构化配置
20. 已完成 Tool / Workflow 路由摘要信息透传到 `agent-core`，决策阶段可优先基于结构化 `metadata.routing` 做匹配
21. 已完成模型管理基础能力规划并落首版实现，支持模型配置维护、字典分类和 Agent 默认模型下拉绑定
22. 已完成 Knowledge 管理的 Embedding 模型下拉选择，仅展示已启用的向量模型，并在后端校验模型类型与状态
23. 已完成 Knowledge 文档上传解析首版，支持 PDF、DOCX、Markdown、TXT 原始文件持久化和解析后手动发布索引
24. 已修复 Knowledge 大文件上传被前端 Nginx 默认 1MB 限制拦截的问题，统一网关与后端 20MB 限制，并补充前端大小校验、上传超时和可读错误提示
25. 已完成 Knowledge 上传文档在线预览：PDF 通过受鉴权原始文件接口内嵌展示，DOCX、Markdown、TXT 使用安全的解析文本预览，并校验知识库与文档归属及存储路径
26. 已修复 Knowledge 混合检索 SQL 占位符与参数绑定顺序不一致的问题，关键词、向量、知识库范围、阈值和 Top K 严格按 SQL 顺序传递，并增加仓储层回归测试
27. 已补齐图片型 PDF 解析质量门禁与本地 OCR：文本层质量不足时使用 Tesseract 中文逐页识别，清理重复页眉页脚和水印，支持原始文件重新解析并使旧索引失效；前端增加“重新解析”入口
28. 已优化 Knowledge 评测交互：“按当前问题评测”和“评测历史”均增加执行中状态、成功与失败提示，将 Recall@K、MRR、耗时和历史记录就近展示，避免结果被长检索文本挤到页面底部
29. 已增强 DOCX 知识文档解析：按正文顺序提取段落和表格结构，对内嵌栅格图片使用 Tesseract 中英文 OCR，记录内嵌图片数和成功识别数，并延长上传超时以支持图片型 Word

---

## 3.4 第四阶段：执行引擎最小闭环

目标：

1. 完成 `agent-core` 会话管理
2. 完成消息记录
3. 完成最小任务执行链路
4. 先提供模拟响应或最小可执行响应

交付物：

1. 会话创建接口
2. 会话列表接口
3. 消息列表接口
4. 发送消息接口
5. 最小执行链路说明

状态：进行中

当前完成情况：

1. 已完成 `agent-core` 真实库接入与初始化 SQL
2. 已完成本地 JWT 校验与运行时鉴权骨架
3. 已完成会话创建接口
4. 已完成会话列表接口
5. 已完成消息列表接口
6. 已完成发送消息接口
7. 已完成前端会话中心页面
8. 已完成从 Agent 管理页进入会话页的跳转接入
9. 已完成 `tasks`、`task_steps` 真实落库
10. 已完成会话执行记录接口
11. 已将完整执行记录迁移到独立任务运行中心，会话页仅保留最新运行摘要与深链入口
12. 已完成 `Tool Router` 最小骨架和平台默认 `DOC_SEARCH` 真实执行
13. 已完成教育类 `SCORE_ANALYSIS` 真实执行，并支持 `inherit_and_append` 模式下的 Agent Tool 路由验证
14. 已完成 `ExecutionContext` 统一运行时上下文对象及分来源构建逻辑，并接入消息发送执行链路
15. 已完成规则版 `ExecutionDecision` 与 `ExecutionDecisionService`，形成“上下文 -> 决策 -> 执行”基础链路
16. 已完成最小 `ExecutionPlan` 与 `ExecutionPlanService`，形成“上下文 -> 决策 -> 计划 -> 执行”基础链路
17. 已完成 `tool-router` 通用化改造，并通过 `ToolExecutor` 接口接入文档检索与分数分析执行器
18. 已完成 `workflow` 最小执行骨架，支持通过 `Workflow` 定义执行 `start -> tool -> end` 最小链路
19. 已完成 `ExecutionPlan` 多步 Tool 顺序执行，支持按固定优先级生成多步 Tool 计划并逐步落 `task_step`
20. 已细化 `task` / `task_step` 执行状态流转，补充失败状态回写与错误信息沉淀
21. 已完成任务运行中心执行时间线展示，支持查看步骤状态、时间范围、输入快照、输出快照和错误信息
22. 已扩展 `Workflow` 节点能力，支持 `condition` 节点分支判断、多节点顺序遍历以及 `start -> condition -> tool -> end` 最小链路
23. 已完成多步执行结果传递与汇总，支持将前序步骤结果注入后续步骤输入，并在最终回复中输出步骤摘要和详细结果
24. 已完成 `Workflow` 节点结果透传与上下文增强，支持将前序节点结果传入条件判断和后续 Tool 节点输入
25. 已完善 Demo 链路，支持 Dashboard 快捷 Demo 入口和会话页示例问题带入
26. 已完成 `Prompt` 真接入执行链路，支持真实拉取 Prompt 正文并进入 `ExecutionContext`、步骤输入快照、Tool 输入和 Workflow 节点输入
27. 已补充平台默认 `TIME_INFO` Tool 骨架，支持时间/日期/时区类请求的真实执行
28. 已优化 Demo 文案与展示，支持 Dashboard 执行链路说明和会话页示例问题快捷入口
29. 已优化 Demo 快捷入口的 Agent 能力匹配逻辑，支持按实际 Prompt / Workflow / Tool 范围展示当前演示链路
30. 已增强会话页架构链路概览，支持直接查看最近一次执行的运行时绑定、决策结果和计划步骤
31. 已完成用户级 `Memory` 最小能力，支持沉淀跨会话偏好与关注点，并在执行前注入 `ExecutionContext`
32. 已完成执行级 `Memory` 最小能力，支持沉淀任务摘要、关键发现和最终响应摘要，并在执行时间线展示
33. 已完成 `MemoryResolverService / MemoryUpdaterService` 职责拆分，Memory 读写逻辑从 `ConversationService` 中收口为独立组件
34. 已完成 `MemorySelectorService / MemoryInjectionService`，支持按 direct reply、Tool、Workflow 三类执行路径选择记忆并统一注入输入文本
35. 已完成 `Recovery / Retry` 最小能力，支持 `tool` 步骤失败后自动重试 1 次，并沉淀 `retry_count` 与失败类型
36. 已将当前 `Recovery / Retry` 规则正式沉淀到双服务开发约定文档，明确重试范围、失败类型、状态回写和事务限制
37. 已完成失败状态持久化拆分，`sendMessage()` 不再使用单个大事务，失败态 `task / task_step` 可保留用于后续恢复
38. 已完成从失败步骤恢复执行的最小闭环，支持复用前序成功步骤结果并从首个失败步骤继续执行
39. 已完成恢复前校验与恢复上下文回放，恢复执行前会校验计划一致性，并在新步骤输入快照中写入恢复来源与前序步骤摘要
40. 已完成 `workflow` 节点级恢复最小能力，Workflow 失败时可携带失败节点与已执行节点快照继续恢复
41. 已完成 `approval` 最小骨架，Workflow 命中审批节点时可挂起为待审批，并支持审批通过后继续执行、审批拒绝后结束任务
42. 已补审批 Demo 初始化数据与前端快捷入口，支持通过 `Approval Demo` 直接进入审批挂起演示链路
43. 已修正 `ExecutionDecision` 的流程优先级，当消息明确命中流程/审批意图时，绑定 Workflow 的 Agent 会优先进入 Workflow 分支
44. 已支持审批通过 / 拒绝时填写审批说明，并在会话执行时间线中展示审批说明
45. 已完成用户级长期事实与长期目标记忆最小拆分，并接入会话详情展示
46. 已细化 `Memory` 注入策略，支持按 direct reply、文档检索、分数分析、Workflow 等不同执行场景选择不同记忆项，并将 `memoryStrategy / selectedMemoryItems` 写入步骤输入快照
47. 已细化 `Approval / Recovery` 恢复规则，支持审批任务恢复前校验、审批后继续执行失败状态回写，以及审批继续执行元数据回放
48. 已完成 `RecoveryValidationService`，将恢复前计划一致性校验、审批状态校验和审批继续执行校验从 `ConversationService` 中拆分为独立组件
49. 已细化 `workflow` 恢复规则，支持恢复目标节点有效性校验、恢复模式区分，并在 Workflow 输出快照中沉淀 `resumeMode / resumeNodeId / resumeNodeType`
50. 已完成 `MemoryInjectionPolicyService`，将 Prompt、记忆和前序结果的输入拼装规则从 `MemoryInjectionService` 中拆分为独立组件
51. 已完成 `MemoryItemCollectorService`，将 Memory 候选项收集与选择规则拆分，`MemorySelectorService` 只保留策略选择职责
52. 已完成 `Workflow` 变量与节点上下文标准化，统一沉淀 `runtimeVariables / previousNodeResults / nodeName`
53. 已完成 `ExecutionOrchestratorService`，将步骤编排、执行、重试与审批挂起处理从 `ConversationService` 中抽出
54. 已取消会话级默认 Workflow 快照，改为 Agent 维护可用 Workflow 集合，并在任务侧统一沉淀 `execution_type / execution_chain_json` 执行分类与调用链路快照
54. 已完成 `ExecutionResponseAssemblerService`，统一收口 direct reply 和多步骤执行的最终响应拼装
55. 已完成 `ExecutionStatusFlowService`，统一维护 task / conversation 的 processing、success、failed、waiting_approval 状态回写
56. 已完成一轮完整架构链路验证，覆盖 direct reply、单 Tool、多 Tool、approval/workflow、recovery/resume 五条主链路
57. 已清理 `ConversationService` 中遗留的旧编排辅助方法，入口服务仅保留会话入口、恢复上下文与结果回写职责
58. 已完成 `Memory` 保存链路的规则 + `LLM` 联合最小能力，支持规则先产出记忆草稿，再由可开关的 `LLM` 做归一化增强，并在失败时自动回退到纯规则结果
59. 已完成决策阶段对 Tool / Workflow 结构化路由摘要的接入，当前 `ExecutionDecisionService` 在可选 `LLM` 决策输入中会携带 `metadata.routing` 等资源摘要信息
60. 已补充决策阶段上下文边界约定，明确 `LLM` 决策默认仅使用 `decisionPrompt`、用户输入、记忆摘要和可用 Tool / Workflow 清单，不传完整 `Agent Prompt`
61. 已将 `ExecutionDecision` 收敛为资源选择模型，当前决策阶段只返回 `selectedTools / selectedWorkflows`，执行顺序统一下沉到 `ExecutionPlan` 阶段生成
62. 已将 `ExecutionPlan` 升级为富计划模型，补充 `planId / goal / assumptions / constraints / risks / stepId / dependsOn / expectedOutput / failurePolicy` 等规划字段
63. 已新增 `RuntimeLlmGatewayService`、`ExecutionPlannerService`、`PlanValidatorService`，形成“决策收敛资源 -> LLM 任务拆解 -> 服务端校验 -> 执行器消费”的 Planner 主链
64. 已将会话执行链路切换到 `ExecutionPlannerService`，并将 richer plan snapshot 回写到 `execution_chain_json` 与步骤输入快照
65. 当前执行器已正式放行 `tool / workflow / reply / condition / approval / handoff` 六类可执行步骤；`start / end` 等节点类型仍保留在 Planner 模型层，当前不直接下发执行
66. 已为 Agent 新增独立 `plannerPrompt` 绑定链路，覆盖 `registry-center` 后端接口、初始化 SQL、`agent-core` 运行时快照与前端管理页面
67. 已新增 `tasks.current_plan_version`、`task_plans`、`task_plan_steps` 真实落库，并在创建任务、失败恢复、审批恢复三条链路沉淀计划版本快照
68. 已扩展会话执行记录查询结果，支持返回计划版本列表及计划步骤，并在前端执行页展示 Planner 产出的计划头与步骤细节
69. 已为 Planner 计划步骤正式接入 `condition` 执行能力，支持条件步骤产出 true/false 结果，并通过后续步骤 `inputs.runWhen` 控制是否执行
70. 已为 Planner 计划步骤正式接入 `approval` 执行能力，支持计划级审批挂起、审批通过后按 `nextPlanStepNo` 恢复、审批拒绝后终止任务
71. 已完成任务级 `tracing` 最小能力，新增 `tasks.trace_id` 与 `task_traces`，打通上下文构建、决策、规划、步骤执行和任务结果的事件沉淀，并在会话执行页展示追踪事件摘要
72. 已将 `guardrails + tool approval policy` 升级为多层策略中心，支持基于 `metadata.policyCenter.rules` 与兼容旧 `guardrails / approval` 配置，按 `Agent / Tool / 用户 / 环境` 多维命中、优先级决策、统一命中解释，并将审批策略元数据沉淀到 `approvals` 与 `task_traces`
73. 已完成 `handoff / 多 Agent delegation` 最小能力，Planner 可引用可委派 Agent 生成 `handoff` 步骤，执行器可基于目标 Agent 的 Prompt / Model 完成子任务委派并回写结果
74. 已完成 `Tracing` 运营能力第一版，新增独立查询 API、按时间范围 / Agent / 状态聚合、慢步骤排行、审批等待时长统计、CSV 导出和前端问题排查页
75. 已完成 Planner 增强第一版，支持多次自动 `replan`、失败指纹熔断、计划质量评分、计划版本 diff 展示和人工改计划后继续执行
76. 已完成 `Approval / Recovery` 细化第一版，支持审批超时自动失效、审批撤回、驳回后二次规划、会话级审批 SLA 统计和前端恢复操作入口增强
77. 已完成 Tool Runtime 标准错误分类与调用 Hook，支持 `before / after / error` 追踪、可重试错误识别以及 `MCP / external_http` 标准错误码回写
78. 已完成 `Eval Harness` 第二版，支持 `baseline run` 对比、可选 `LLM Judge`、基线差值展示与 CI 批量触发脚本
79. 已完成 `Eval Harness` 数据集版本化第一版，支持 `Suite` 版本快照、`Run` 绑定 `suite_version`、版本列表与 diff 展示
80. 已完成 `Eval Harness` 跨批次 Run 对比第一版，支持同一 `Suite` 下两次 `Run` 的汇总对比与逐用例差异展示
81. 已完成 `Eval Harness / Tracing` 带鉴权 CSV 导出，支持 `Run` 详情导出、`Run Compare` 导出、`Suite Diff` 导出，并修正前端直链下载无法携带 `Authorization` 的问题
82. 已增强 `Eval Harness` 执行治理能力，支持 `manual baseline` 指定、`Suite baseline` 管理、按 `scenario / tags` 选择用例执行、`CI Gate` 阈值判定，以及 `Run` 的 `CSV / JSON / Markdown` 报告导出
83. 已增强 `MCP / 外部 Tool` 运行治理，补充独立 `health check` 接口、`transportType / schemaVersion / healthStatus` 回显，并为 `external_http` 增加 `SSE` 响应兼容
84. 已增强 `Planner` 稳定性，支持复用同 Agent 历史成功计划作为 `template hint` 注入规划输入，并将任务最终执行反馈回写到 `task_plans.metadata.executionFeedback`
85. 已增强 `Tracing` 问题排查能力，补充独立 `trace topology` 接口与前端拓扑抽屉，可联动查看 spans、审批节点和 delegation 链路
86. 已补齐基础 `Approval Workbench`，支持按当前用户可处理范围查询审批记录，并在策略中心页直接执行通过/驳回
87. 已增强 `Approval Workbench` 运营能力，支持按 `conversationCode / taskCode` 检索审批单、展示审批对象/超时截止/等待时长/来源类型，并支持填写审批说明与驳回后二次规划
88. 已增强 `Trace Topology` 结构化排查能力，支持返回链路摘要统计，并在前端以 `Span / Approval / Delegation` 结构化表格展示链路明细
89. 已增强 `Delegation Tree` 运营视图，支持展示任务详情、错误摘要、计划版本、审计链详情和一键联动到会话内 `Trace` 检索区域
90. 已增强会话执行页 `Planner` 可视化，支持展示计划总数、自动重规划次数、人工接管次数、计划来源标签、质量问题和执行反馈
91. 已增强 `Tool` 详情页运行治理视图，支持在详情内发起健康检查并查看最近一次 `healthStatus / transportType / schemaVersion / output`
92. 已增强 `Planner` 版本复盘视图，支持展示计划版本时间线、重规划来源聚合和按版本汇总的执行结果摘要
93. 已完成 `Tool / MCP` 健康检查历史化治理，支持健康检查自动落库、最近失败原因、历史成功率、最近 7 天实例趋势和检查历史列表
94. 已增强 `Tool / MCP` 实例级治理，支持 `SLA` 目标、连续失败告警阈值、实例告警状态和最近相关 `Trace` 联动跳转
95. 已完成运行时上下文压缩第二版，支持基于模型 `contextWindow / reservedOutputTokens / compactionTriggerRatio / tokenizerFamily` 的统一预算分配、`decision / planning / execution_input / reply` 阶段统一压缩入口、长会话滚动摘要压缩、近似 Token 级预算观测、Agent 与会话级 `compactionPolicy` 运行时生效、以及 `task_traces` 与前端排查抽屉/运营指标中的压缩元数据观测

---

## 3.5 第五阶段：前后端联调与 Demo

目标：

1. 完成认证联调
2. 完成会话链路联调
3. 跑通一个最小 Demo
4. 完成基础测试回归

交付物：

1. 联调通过的前后端链路
2. 最小 Demo 使用说明
3. 测试点与回归结果

状态：进行中

当前完成情况：

1. 已完成认证链路前后端联调
2. 已完成会话链路前后端联调
3. 已跑通最小 Demo 主链，覆盖 direct reply、Tool、Workflow、approval、resume、tracing、handoff
4. 已完成自动化测试回归与通用主链稳定性门禁；标准场景验收和 Demo 数据仍需持续打磨
5. 已完成 `Eval Harness` 第一版，支持评测集定义、隔离执行、规则判分、批次结果查询和前端管理页
6. 已补齐 `Eval Harness` 与 `Tracing` 导出链路，前端可直接下载带鉴权的 CSV 结果用于归档和复盘

---

## 4. 当前优先级

当前优先级从高到低如下：

1. Workflow 内部节点观测与主计划 Trace 元数据对齐
2. Prompt Snapshot 跨版本聚合、批量实验与压缩策略对比
3. Tool 输入治理中心与 MCP 安装发现、实例灰度、下线迁移
4. 规模化运行治理与真实环境压测（多租户、成本和配额主链已完成）
5. 稳定 Demo 数据、真实第三方环境矩阵与规模化压测

当前完成情况补充：

1. 已完成会话级 `Memory` 最小能力，支持在 `conversation_memories` 中沉淀最近问题、偏好摘要和最近响应摘要，并在会话页展示
2. 已明确当前 `Recovery / Retry` 只对 `tool` 步骤自动重试，最大重试次数为 1
3. 已完成失败记录独立保留的最小能力，后续可继续增强为更细粒度事务或恢复入口
4. 已完成失败任务恢复入口最小能力，当前支持按任务首个失败步骤恢复，并在前端执行时间线中直接触发
5. 已完成恢复前校验与恢复回放信息沉淀，执行时间线可直接查看恢复来源和回放上下文
6. 已完成 `workflow` 失败最小恢复，当前可基于失败节点 ID 和已执行节点快照继续恢复
7. 已完成 delegation 多级任务树查询与展示，支持递归任务树接口、父任务子结果汇总、跨层级 delegation 审计链和前端树状展示
8. 已完成 Tracing 运营页，支持独立筛选、指标聚合、慢步骤排行、审批等待统计、CSV 导出和问题排查抽屉
9. 已完成 Planner 多次自动重规划、质量评分、plan diff 和人工改计划继续执行闭环
10. 已完成 Approval / Recovery 超时、撤回、驳回后二次规划和会话级 SLA 指标
11. 已完成统一 Cost Ledger、资源价格历史、tenant/project/agent/user 四级日/月预算、阈值告警和执行前硬熔断；LLM、Tool/MCP、Sandbox、Delegation 已接入统一运行时计量网关，并提供独立运营页面
12. 已完成 Agent Run 分布式调度控制面，支持 Worker Group 容量、节点注册与心跳、drain/offline、能力标签匹配、Agent/优先级路由规则、组级背压与可视化拓扑；原有公平队列、lease 和故障接管继续作为数据面执行保障

---

## 5. 串行与并行安排

### 5.1 建议串行

1. 认证模型未确定前，不开始大规模联调
2. 数据结构未稳定前，不开始大规模前后端对接
3. 会话接口未稳定前，不开始聊天页完整交互实现

### 5.2 建议并行

1. `产品Agent` 可并行整理需求和页面说明
2. `DBAgent` 可并行整理认证和会话相关表设计建议
3. `开发Agent` 可在接口清单基本稳定后并行推进前后端实现
4. `测试Agent` 可在开发阶段提前整理测试点

---

## 6. 多 Agent 分工

### 6.1 主控Agent

负责：

1. 总计划制定
2. 阶段推进
3. 依赖协调
4. 完成状态更新

### 6.2 产品Agent

负责：

1. 需求梳理
2. 原型说明
3. 验收标准

### 6.3 DBAgent

负责：

1. 表设计建议
2. 字段设计建议
3. SQL 建议

### 6.4 开发Agent

负责：

1. 代码实现
2. 接口落地
3. 验证方式整理

### 6.5 测试Agent

负责：

1. 测试点
2. 风险点
3. 回归结论

---

## 7. 当前完成情况

已完成：

1. 双服务目录初始化
2. 根 `Maven` 聚合工程初始化
3. `agent-core` 基础 Spring Boot 骨架初始化
4. `registry-center` 基础 Spring Boot 骨架初始化
5. `frontend-web` 基础 `Vue 3 + TypeScript + Vite` 骨架初始化
6. 项目级开发规范落地
7. 多 Agent 协作规则落地
8. 项目已接入 Git 并推送远端
9. 产品侧 MVP 原型说明初稿已输出到 `docs/prototype/原型说明.md`
10. 独立 HTML 页面原型已输出到 `docs/prototype/页面原型预览.html`
11. 原型范围已补充用户、角色与 Agent 授权模块
12. 已完成一次多 Agent 原型评审，评审结果已输出到 `docs/prototype/原型评审意见.md`
13. 已根据评审意见完成原型修正，明确权限页结构、会话 Agent 规则、Dashboard 收敛方向与状态/异常说明
14. 已完成数据库设计初稿，输出到 `docs/数据库设计.md`
15. 已补充规则：后续所有表设计相关修改必须同步更新 `docs/数据库设计.md`
16. 已完成 `registry-center` 接口清单初稿，输出到 `docs/registry-center接口清单.md`
17. 已新增 `registry-center` 并行开发子计划，输出到 `docs/registry-center并行开发计划.md`
18. 已完成 `registry-center` 登录接口、当前用户接口与 `JWT` 鉴权链路
19. 已完成 `registry-center` 认证模块和前端登录页第一批开发，并完成登录 / `me` 接口验证
20. 已将 `registry-center` 认证模块切换到真实 `PostgreSQL`，并补充初始化 SQL 脚本
21. 已在真实 `PostgreSQL` 环境完成 `registry-center` 认证链路验证
22. 已补充 `model`、`vo`、`dto` 字段注释规范，并同步修正当前认证模块相关文件注释
23. 已完成 `registry-center` 用户模块后端实现，覆盖分页查询、详情、新增、编辑、启用/停用、重置密码接口
24. 已完成 `registry-center` Tool 模块后端实现，覆盖分页查询、详情、新增、编辑、启用/停用接口
25. 已完成前端 Tool 管理页第一批开发，并完成真实 PostgreSQL 联调验证
26. 已完成 `registry-center` Workflow 模块后端实现，覆盖分页查询、详情、新增、编辑、发布、停用接口
27. 已完成前端 Workflow 管理页第一批开发，并完成真实 PostgreSQL 联调验证
28. 已完成 `registry-center` Prompt 模块后端实现，覆盖分页查询、详情、新增、编辑、启用、停用接口
29. 已完成前端 Prompt 管理页第一批开发，并完成真实 PostgreSQL 联调验证
24. 已完成 `registry-center` 角色模块后端实现，覆盖分页查询、详情、新增、编辑、启用/停用接口
25. 已完成 `registry-center` 用户角色绑定后端实现，覆盖查询与覆盖式更新接口
26. 已完成 `registry-center` 角色 Agent 绑定后端实现，覆盖查询与覆盖式更新接口
27. 已完成 `registry-center` Agent 模块第一批后端实现，覆盖分页查询、详情、新增、编辑、启用/停用接口
28. 已完成前端用户管理页第一批开发，并完成用户分页、详情、新增、编辑、启用/停用、重置密码接口联调验证
29. 已完成用户绑定角色基础能力，并完成查询、覆盖更新、清空恢复的真实库联调验证
30. 已完成前端角色管理页第一批开发，并完成角色分页、详情、新增、编辑、启用/停用接口联调验证
31. 已完成角色绑定 Agent 基础能力，覆盖查询和覆盖式更新接口
32. 已完成 Agent 最小查询能力，覆盖分页查询和详情接口，并可支撑前端绑定 Agent
33. 已完成前端 Agent 管理页第一批开发，并完成真实 `PostgreSQL` 环境下的新增、详情、编辑、启用、停用接口联调验证
34. 已完成 Agent Tool 范围维护能力，并完成真实 `PostgreSQL` 环境下的查询、覆盖更新和继承默认模式联调验证

当前 MVP / 通用 Agent 平台功能目标已完成。剩余事项不再归类为功能缺口，而是生产规模和商业运营增强：

执行主链增强补充：Planner 已接入历史计划真实结果反馈，按 Agent 与用户提取最近成功/失败计划，将成功模式、失败原因和改进建议结构化注入后续规划，实现“计划 -> 执行结果 -> 后续规划”的反馈闭环。

Checkpoint 已补齐副作用模糊状态恢复：进程中断时联查 Tool 副作用状态，安全步骤自动续跑，不确定或不可逆副作用强制人工核验，并支持确认成功、授权重试、确认已补偿三类可审计处置。

跨步骤数据流已升级为版本化 `dataContract`：支持基于 `sourceStepId + JSON Pointer` 的结构化字段绑定、必填和基础类型校验，并在步骤成功落库前校验统一输出快照，契约错误会明确阻断执行。

Human-in-the-loop 已补齐计划影响预览和持久化步骤边界控制，支持暂停、恢复、跳过指定步骤和单步执行；控制行为独立审计，避免在 Tool 调用中间强制中断外部副作用。

长任务等待已从线程阻塞升级为 timer/event/webhook 持久化订阅，等待时释放 Worker，信号到达后自动重新入队并从 Checkpoint 续跑，支持跨进程和跨节点恢复。

执行错误已统一为稳定 `errorCode` 与 `retryable/replannable/compensatable/approval_required/fatal` 语义，步骤快照、Trace 和自动 Replan 共用同一分类结果，禁止再按错误文案猜测恢复策略。

结果可信度治理已接入主链，Agent 可按 `trustPolicy` 配置最低置信度、独立证据来源数、引用要求及 `warn/replan/approval/block` 动作，命中结果进入 Trace 并复用现有 Replan 和 Approval 链。

主链职责继续收敛：任务计划版本持久化、计划 Diff 元数据、历史执行反馈和 Planner 模板解析已迁移到独立 `TaskPlanLifecycleService`，并以独立事务维护计划版本一致性；`ConversationService` 只负责调用编排。

1. 在多节点真实集群执行容量、故障注入和持续压测，形成容量模型与 SLO
2. 接入外部 KMS/Vault、镜像签名、SBOM 与依赖漏洞扫描
3. 接入真实供应商 Tool/MCP/LLM 认证环境，持续积累版本兼容矩阵
4. 扩展账单对账、多币种汇率、数据归档和企业审计导出
5. 建立 Prompt、压缩、Tool、Replan、Delegation 的主动告警与外部 On-call 集成
6. 按需扩展记忆冲突消解、批量实验和跨模型/压缩策略对比

---

## 8. 下一步行动

下一步进入生产验收阶段，优先执行真实集群压测、供应链安全和第三方供应商兼容性认证。当前完成度和验收基线见 `docs/平台完成度与后续路线.md`。

当前并行执行计划见：

1. `docs/registry-center并行开发计划.md`
2. `docs/Codex-Harness运行内核改造计划.md`

---

## 9. 计划更新记录

初始版本：

1. 说明：用于约束 MVP 推进顺序，并要求每次开发任务完成后更新状态

本次更新：

1. 产品原型已从文档说明进一步落到独立 HTML 原型文件
2. 用户、角色、用户绑定角色、角色绑定 Agent 已补充到原型说明与 HTML 原型中
2. 当前可直接打开 `docs/prototype/页面原型预览.html` 查看页面原型
3. 已完成执行链路 P0 清理，去除 `ExecutionDecision`、`Memory` 与前端页面中的核心演示硬编码
4. 已为 `direct_reply` 与计划 `reply` 步骤接入运行时 `LLM` 回复生成，模板拼接仅保留为降级兜底
5. 已为 Planner `condition` 步骤与 Workflow `condition` 节点补充 `LLM` 语义判断模式，规则判断与语义判断并存
6. 已完成 `tracing`、多层 `guardrails + tool approval policy`、`handoff / 多 Agent delegation` 三项增强主链，并完成提交与推送
7. 已将“未完成项”更新为当前代码真实剩余项，不再沿用早期“注册能力/最小闭环未完成”的旧状态
8. 已新增“平台级后续 TODO”，用于记录当前平台从准生产走向成熟商业级仍需补齐的关键能力
9. 已完成上下文压缩高阶能力主链增强，补齐模型级 Token 预算、结构化保护事实、分层摘要元信息、压缩治理页与导出能力
10. 已完成 Prompt 组装可解释化，支持 `promptAssembly`、`promptSnapshot`、`final prompt preview`、`diff` 和真实 `systemPrompt / userPrompt` Trace 观测
11. 已完成运行时步骤 `promptTrace` 观测，覆盖 `reply / reflection / delegation`，并在 Trace 排查页支持直接查看步骤级 Prompt 预览
12. 已完成 Tool 输入观测落库，支持记录 `raw/final input`、长度裁剪、预算和压缩上下文，供后续 Tool 输入治理中心复用
13. 已将“未完成 / 平台级 TODO / 下一步行动”更新为当前代码真实状态，统一以 Prompt 治理、Tool 治理、Trace 联动与回放治理作为后续主线
14. 已完成模型凭据安全治理，模型 API Key 改为 AES-GCM 加密落库，管理接口仅返回配置状态和脱敏值，运行时通过服务令牌保护的独立接口读取
15. 已完成平台接口 RBAC 第一版，JWT 同时承载角色和菜单 Scope 权限，Registry 运行时读取使用独立 `SERVICE_RUNTIME` 身份，前端路由和菜单同步按权限收口
16. 已完成异步持久化 Agent Run 第一版，支持 PostgreSQL 队列、`SKIP LOCKED` 多实例领取、租约心跳、幂等入队、取消中断、失败保留和运行状态查询
17. 已建立自动化测试与 CI 第一版，统一执行双后端单元测试、前端类型检查和生产构建，并覆盖安全与异步运行关键回归点
18. 已将双服务数据库变更治理切换到 Flyway，兼容既有实例 baseline 与全新实例初始化，并为核心运行关系补充外键和状态约束
19. 已建立 Plan / Workflow 共享的 `ExecutionStepType` 执行契约，统一可执行范围、终止语义、事实产出语义和 Trace 类型，作为合并公共节点执行器的基础
20. 已建立确定性的 `ToolExecutorRegistry` 扩展注册机制，支持独立业务 Jar 自动发现并阻断 Tool code 冲突，为高考 Demo Tool 迁出核心提供兼容边界
21. 已将 LLM 调用切换为 `LlmProviderAdapter` 路由，OpenAI-compatible 协议独立实现，补充标准错误分类和 Token usage 解析，未实现供应商不再伪兼容调用
22. 已在 `TaskTraceService` 统一持久化入口增加递归敏感字段脱敏、文本凭据清洗和快照长度上限，所有 Trace 调用点默认受保护
23. 已将 Guardrail 扩展到最终输出阶段，复用策略中心的作用域、灰度、版本和命中审计，支持输出正则/关键词脱敏与安全阻断，并覆盖所有 assistant 落库和记忆写回路径
24. 已增强 MCP Streamable HTTP 协议客户端，支持当前稳定协议版本协商、JSON/SSE 双响应、JSON-RPC 错误、Tool `isError`、initialized 生命周期和 session 404 自动重建
25. 已移除 MCP legacy `sse` 的伪兼容声明，运行时返回专用协议错误并引导使用 `streamable_http`，避免健康检查和正式调用产生错误成功判定
26. 已接入 Micrometer OpenTelemetry tracing bridge，建立双服务 W3C Trace Context、注册中心 HTTP 自动传播，以及异步 Agent Run 与 Tool 调用标准 Observation span
27. 已补齐 LLM 标准 Observation、Token/耗时指标，并为 LLM、MCP、External HTTP 的 JDK HttpClient 请求注入当前 W3C trace context
28. 已完成 Workflow 公共执行契约第一阶段落地，新增确定性的 `ExecutionStepHandlerRegistry`，Plan 全部标准步骤及 Workflow 的公共 Agentic 节点统一通过同一处理器执行，重复类型注册会在启动时阻断
29. 已完成 Prompt / Tool / Trace 联动排查与无副作用回放，支持从 Trace 提取完整 Prompt 快照、Tool 输入观测和 Tool Code，并可使用 Agent 默认模型或指定模型执行 replay、返回输出 Diff 与 Token/耗时数据
30. 已独立实现 MCP `stdio` 与 legacy HTTP+SSE transport，补齐进程/长连接会话生命周期、initialize/tools/list/tools/call、超时清理、健康检查、注册字典、配置校验和真实协议自动化测试
31. 已补齐通用 Agent 自动化集成与稳定性回归，覆盖资源装配、Workflow 公共节点、MCP 协议和非领域四轮会话状态，并移除核心会话状态中的高考业务词表
32. 已统一 Workflow 内部节点观测，所有节点类型均通过 `NodeTraceScope` 记录 started / succeeded / failed / waiting 生命周期，关联父 `taskStepId`，并统一沉淀 `promptTrace / toolInputObservation / runtimeVariables / recovery context`
33. 已完成分布式 Agent Run 稳定性增强，支持持久化优先级与 aging、用户/Agent 公平调度、全局与单用户背压、批量入队/领取、租约隔离、指数退避、优雅停机和队列指标 API，并通过真实 PostgreSQL 双 Worker 并发验证
34. 已完成 Sandbox 生产化闭环，通过 Docker Engine API 执行强隔离容器，支持独立工作区、非 root、只读 RootFS、`CapDrop ALL`、`no-new-privileges`、网络/命令策略、资源上限、取消清理、完整审计和管理页面，无宿主机执行降级
35. 已完成 Knowledge 生产化闭环，支持知识库/来源/文档版本、Agent 绑定、角色权限、全量与增量索引、`pgvector` 混合检索、`[K1]` 引用、自动上下文注入、检索审计及 Recall@K / MRR 评测工作台
36. 已完成 Tool / MCP 全生命周期治理，支持 Catalog 发现安装、远程 `tools/list`、不可变版本、实例灰度、兼容性迁移、依赖影响、`draining`、安全下线、实例熔断/半开/自动恢复、故障切换和前端治理抽屉
37. 已完成平台主链 E2E 门禁，使用隔离 pgvector、真实双服务 HTTP 与 Mock OpenAI-compatible LLM 覆盖资源创建、同步多轮、历史连续性、Decision、Planner、Task/Plan/Step、异步 Worker、Trace、拓扑和队列指标，并修复首轮 Decision 空上下文 NPE、Model INSERT 字段错位及 Step 类型落库失真
38. 已完成多租户与组织隔离，支持 Tenant / Organization / Workspace / Project 层级、用户成员授权、默认作用域、JWT 无状态切换、Registry 资源与 Core 运行数据归属，以及连接级 PostgreSQL RLS；E2E 使用非超级用户验证跨租户 Model 不可见并在第二租户完成同步、多轮和异步主链
39. 已完成 Prompt 实验与 Tool 输入契约治理，支持 Prompt 不可变版本、会话/用户稳定分流、真实 Task/成本归因、胜出发布、Contract 版本、发布前样例回归、敏感输入哈希观测及独立治理页面；平台 E2E 已验证同一会话多次执行稳定命中同一 Variant
40. 已完成外部 Tool / MCP 实例级兼容性矩阵，统一执行配置、连接/初始化、发现和真实调用四阶段测试，结果历史化落库并提供全局矩阵 API；协议基线覆盖 Streamable HTTP、stdio、legacy SSE 与外部 HTTP GET/POST/SSE
41. 已将高考志愿领域 Tool、专项解析、LLM fallback 与知识样例整体迁移到独立 `agent-tool-plugin-gaokao` Maven 插件模块，`agent-core` 不再编译包含业务关键词和业务规则的 Tool 实现，未安装插件时通过 Executor 健康检查显式失败而非伪执行
42. 已扩展 CI 稳定性与平台 E2E 门禁，长会话跨越摘要阈值后验证首轮事实连续性，并发提交四个持久化 Run 并逐个校验完成；同一流程继续覆盖 Flyway 全新迁移、非超级用户 RLS、Worker lease、MCP 协议、Trace 脱敏、熔断与成本/实验归因
43. 已移除 Workflow 静默 fallback，非法图统一显式失败并保留节点级 Recovery 现场；拆分会话 Recovery/策略、上下文压缩策略解析、Workflow 图导航/结果组装及状态独立事务；测试门禁新增 Registry 服务测试、真实 Chromium E2E、双向跨租户越权和 Worker/Tool/Workflow/LLM/Sandbox 故障注入矩阵，并修复同会话多 Worker 并发写消息的序号竞争
44. 已完成 Workflow 发布前静态校验，覆盖 canonical node/edge 格式、节点类型、唯一 start/end、Tool/Handoff 必填资源、Condition 双分支、悬空边、重复边、不可达节点、无 end 路径和循环；创建/编辑直接发布及发布动作均受门禁保护，前端支持发布预检并统一生成 canonical graph
45. 已完成副作用 Tool 的幂等与补偿治理，支持 Registry 配置副作用等级、自动或强制自定义幂等键、数据库原子占位、成功结果复用、输入冲突阻断、不确定状态保护及任务最终失败后的逆序补偿
46. 已完成 durable Checkpoint 与自动恢复，Plan Step 各状态边界持久化原计划、决策和结果；Worker 租约重领或瞬时故障重试时复用原 Task/消息，从最新 Checkpoint 进入现有 Recovery 主链，并与副作用 Tool 幂等语义联动
47. 已完成 Agent Eval 发布门禁，Registry Agent 可配置最低平均分、通过率、样本数和报告有效期；所有启用入口统一调用 Core 最新 Eval Run 判定，Core 不可达、无报告、失败或过期均 fail-closed 阻断发布
48. 已完成 Decision/Replan 策略中心运行时闭环，Agent metadata 可配置 LLM 决策失败动作、自动 Replan 开关、最大次数、重复失败熔断和最低计划质量；Core 统一解析安全边界并在 Decision/Replan Trace 中记录实际生效策略
49. 已完成 Memory 与 Delegation 高阶治理：长期记忆支持来源、置信度、TTL、撤销和版本替代并按有效状态注入；委派支持能力自动选 Agent、深度/扇出/超时/回环策略、治理审计字段及父任务取消递归传播
50. 已完善人工计划控制状态机：Task/Step/Run 使用正式 `paused` 状态，`resume` 可同步恢复或异步重新入队，单步控制只放行当前步骤，并支持从指定 Checkpoint 创建独立 Task/Trace/Plan 分支继续执行
51. 已完善长任务持久等待生产语义：同步 Task 等待时自动提升为持久化 Run，Timer/Event/Webhook 均可跨进程重新入队；外部 Webhook 使用 HMAC-SHA256、时间窗口和 nonce 唯一记录完成签名及防重放校验
52. 已完善副作用不确定状态恢复：多个 Tool execution 必须逐条确认结果、授权重试或执行补偿；补偿动作重建原 Task 上下文并真实调用配置的 compensation Tool，所有成功和失败处置独立落库审计
53. 已完善版本化步骤数据契约：支持嵌套 targetPointer 绑定、递归 Schema、组合规则、数组和数值约束，Planner 校验生产者版本兼容范围及上下游类型，未支持 Schema 关键字明确失败
54. 已完善结果可信度治理：支持结构化 Claim、逐 Claim 证据覆盖、独立来源去重、来源权威度、支持/反驳冲突检测，以及独立 LLM/Tool verifier；新增策略全部可在 Agent 页面配置并进入步骤 Trace
55. 已修复 Registry 存量 Tool 生命周期迁移：共享 Provider Instance 不再产生重复 deployment_code，多 Binding Tool 只生成一个确定性基线 Deployment，并在真实存量 PostgreSQL 实例完成迁移验证
56. 已完成前端使用端与管理端职责分层：登录后默认进入会话窗口，会话中心按授权 Agent 聚合用户会话，承载会话创建、名称管理、消息交互、当前会话策略及本次执行的即时审批；Task/Step、Plan/Replan、Approval/Recovery、Delegation 与 Trace 的完整查询和运营治理统一进入管理后台
57. 已完成会话级原生流式响应：发送区可独立启停并持久化到会话 metadata；MiniMax/OpenAI-compatible 请求使用 `stream=true`，模型 `delta.content` 通过持久化 Run 事件跨实例实时转发并支持断线续传；仅最终用户回复允许外发，结束时用 Output Guardrails 校验后的落库消息执行 `replace` 校准，连接异常自动回退轮询链路
## 2026-07-22 会话执行进度与回复效率优化

1. 已将上下文构建、Decision、Planner、Plan Step、最终回复生成接入持久化 `progress` SSE，慢模型执行期间可持续反馈安全运行阶段。
2. 已将最后一个 `reply / clarify` 步骤纳入原生流式边界，并复用其用户回复，禁止末尾再次调用 LLM 做同义汇总。
3. 已收紧 Planner 约束，只有高风险、冲突、低置信度或明确复核要求才生成 `reflection`，Workflow 已产出用户回复时不再追加重复回复步骤。
4. 已新增 `response_assembly` Trace，后续可直接区分模型生成耗时与平台收口耗时。
5. Decision 已收敛为 `direct_reply` 且未选择 Tool/Workflow 时跳过 LLM Planner，直接生成单步规则计划，避免无执行资源场景的固定模型开销。
6. Workflow 新增 `metadata.routing.outputMode=user_reply` 输出契约；Plan Validator 按配置识别回复终点并删除冗余尾部 `reply`，不按业务 Workflow code 写死。
7. Planner 对模型误填的 Workflow code/name 自动映射为 `targetWorkflowId`；规则降级计划同样遵守 Workflow 输出契约，避免异常降级后重新引入重复回复。
8. `user_reply` Workflow 的原始 `responseText` 直接进入输出 Guardrails 和最终消息，禁止 Workflow 调试包装文本覆盖已流出的用户正文。
9. 已将 Workflow metadata 纳入执行快照，并在 Workflow 结果组装层保留 `user_reply` 完整正文；迁移同步修复历史错误包装消息。
10. 已完成 Agent 自适应执行策略，支持 `llm_first/rule_first/rules_only` Decision 模式和 `adaptive/always_plan` Planner 模式；明确资源命中与单 `user_reply` Workflow 可跳过冗余模型规划。
11. 已在统一 LLM Gateway 增加单次 Run 物理调用硬预算，所有阶段、并行步骤和 Delegation 共享额度，超限通过 `BudgetExceededException` 显式终止且不可被 fallback 吞掉。
12. 已完成阶段模型路由，Agent 页面可分别配置 Decision、Planner、Reply、Reflection、Memory、Delegation 模型，运行时一次解析快照并在未配置时安全回退默认模型。
13. 已根据真实 Run 回归清理快速路径观测噪音：跳过 Planner 时不再组装或保存无效 Planner Prompt；计划质量评分将 `outputMode=user_reply` Workflow 识别为合法最终输出。
14. 已将相同观测语义扩展到 Decision：规则优先或仅规则模式未调用模型时只记录 `decisionStrategy` 和跳过原因，不再保存未执行的 Decision Prompt 快照。
15. 已统一平台时间语义：数据库无时区时间继续按 UTC 保存，前端公共格式化入口统一转换为 `Asia/Shanghai`；Agent 时间上下文和内置时间 Tool 显式使用东八区，不再依赖容器默认时区。
16. 已补齐平台通用 Tool 基线，正式实现并注册 `TIME_INFO / CALCULATOR / KNOWLEDGE_SEARCH / HTTP_FETCH / WEB_SEARCH / DOCUMENT_PARSE / WORKSPACE_FILE / SANDBOX_EXECUTE`，统一经过 Tool Router、Schema、Guardrail、Trace、成本与审批链。
17. 已补齐 MCP 配置化接入与本地 Runtime，覆盖 Filesystem stdio、PostgreSQL 只读 stdio 与 GitHub Streamable HTTP；固定版本镜像预装 Node 与 MCP Server，Registry/Core 共享持久目录和环境变量，Lifecycle 安装同步创建 Provider/Instance/Binding，Filesystem 与 PostgreSQL 已完成真实 `tools/list` 发现。
18. 已完成 Tool Capability Pack 注册中心能力，支持能力包 CRUD、Tool 成员、Agent 多能力包绑定、默认范围合并和前端可视化配置；网络、文件写入与 Sandbox 不进入默认继承集合。
19. 已完成通用 Agent 的 Tool / MCP 端到端验收与执行链修复：PostgreSQL MCP 真实查询成功且写操作被只读事务拒绝，Filesystem MCP 写入经 Guardrail 审批、Checkpoint 恢复后真实落盘，15 个远程 Tool 健康检查全部通过；同步修复终态目标判断、审批恢复重放、Planner 安全降级、Tool fallback Schema 兼容和 Task Step 软删除唯一性问题。GitHub MCP 仅剩真实 Token 外部配置与供应商环境验收，不属于代码功能缺口。

## 2026-08-25 前端同源访问修复

1. 已移除前端 Registry/Core API 的局域网 IP 硬编码，生产容器统一通过 Nginx 同源代理 `/api/registry`、`/api/core`，Vite 本地开发同步代理到两个后端默认端口，避免 DHCP 地址变化引发登录和运行接口不可用。
2. 已修复 Context Compaction 运营分页和指标查询中的 JSONB 存在性判断，使用 `jsonb_exists` 替代会被 `JdbcTemplate` 误识别为参数占位符的 `?` 运算符，并补充仓储层回归测试。
3. 已修复直接问答生成成功但 Observation 要求 Replan 时无结果返回的问题：平台生成的最终 `reply` 步骤统一允许重规划，失败时持久化可见的助手错误消息，并从后续模型上下文中排除失败消息，避免错误提示污染对话。
4. 已修复 PDF 文件生成请求误入通用回复 Workflow 的问题：明确的 PDF 生成/导出意图在 `SANDBOX_EXECUTE` 可用时优先路由到实际产物执行链；允许重规划的 Workflow 步骤统一使用 `failure_policy=replan`；LLM Planner 异常降级时补齐 `command/inputFiles/artifactPaths`；Docker Socket 部署显式配置宿主工作区根目录；成功产物由内核追加受权限保护的下载链接，Observation 确认产物后直接结束，避免重复 Sandbox 审批与执行。
5. 已将高风险 Tool 审批入口回归会话主链：异步 Run 进入 `waiting_approval` 或用户重新打开存在待审批任务的会话时，对话窗口自动弹出审批详情，支持填写意见、通过后按 Checkpoint 续跑、驳回后执行恢复策略；任务运行中心继续保留运营补充入口。
6. 已修复 PDF 降级生成的正文错选与中文空白问题：生成前从完整会话历史中按引用主题定位最近有效业务回复，排除审批、失败和产物状态消息；Sandbox 镜像加入固定版本 `ReportLab` 与文泉驿中文字体，生成脚本支持 A4 分页、标题、表格、列表和页码，并通过真实 AI 自学路线 4 页 PDF 的文本与渲染校验。
7. 已收敛会话内审批交互与消息语义：审批、外部事件等待、人工暂停及审批拒绝只作为 Task/Run 控制状态，不再持久化为 Agent 聊天回答；历史控制消息由前端兼容隐藏；通过审批后弹窗立即关闭并在聊天区展示断点续跑状态；审批恢复按稳定 `stepId` 匹配重规划后的计划步骤，避免任务累计 `step_no` 与新计划局部编号错位。
8. 已补齐最终回复与结构化产物的一致性治理：Reply Prompt 只注入 artifact 路径、大小与可下载标记，不携带 Base64 大字段；最终回复以步骤产物快照为事实源，发现“产物已生成但回复声称未生成/无链接/审批未通过”时确定性改写为正确交付说明；历史已落库矛盾回复在查询层兼容校正，并从后续模型上下文中排除污染摘要。
9. 已补齐内容驱动的 PDF 产物命名：优先采用用户显式文件名，其次提取正文 Markdown 标题和请求主题，统一清理路径字符、空白与超长名称；降级生成脚本、`artifactPaths`、下载链接和 UTF-8 下载响应使用同一名称，Planner 同步接收 `suggestedArtifactFilename` 并约束命令输出路径一致。

## 2026-08-31 Codex Harness 方向运行内核改造计划

1. 已完成当前平台与 Codex harness 的定位和能力对比，明确保留 `registry-center` 企业治理控制面和现有 Workflow，不使用 Codex harness 直接替换平台。
2. 已新增 `docs/Codex-Harness运行内核改造计划.md`，按“Runtime Protocol -> Simulation Replay -> AgentLoopKernel -> Runtime Plugin -> Capability Policy -> 双执行模式 -> SDK”拆分实施阶段。
3. 第一批实施范围收敛为统一 Runtime Event Protocol，包括事件状态机、持久化事实源、旧 SSE 兼容投影、Tool/Approval/Artifact/终态事件和前端统一消费器。
4. 专项计划首次创建时未变更数据库、接口或运行代码；进入首个功能实现后已按规则同步更新 `docs/数据库设计.md`。
5. 已完成 M1 首个 Runtime Event 纵向切片：复用 `agent_run_stream_events` 事件事实源，新增 Payload 版本、统一 `RuntimeEventEnvelope / RuntimeEventPublisher`、标准 `message.reset / message.delta / run.progress` 事件、历史事件兼容映射和 `/api/core/runtime/runs/{runCode}/events` 游标续传 SSE；旧会话 SSE 保持兼容。
6. 已完成 M1 第二个 Runtime Event 纵向切片：接入 `run.queued / started / waiting / completed / failed / cancelled` 和 `step.started / completed / failed / waiting`，Run 终态与终态事件原子提交，Step 生命周期事件携带稳定 `taskStepId / stepId` 并映射为 Envelope `itemId`。
7. 已完成 M1 第三个 Runtime Event 纵向切片：统一 Tool Router 接入 `tool.call.requested / started / completed / failed`，同一次调用使用稳定 `toolCallId` 并映射为 Envelope `itemId`；事件仅保存参数名、输入/输出长度和错误分类，避免敏感参数及 Tool 正文进入事件流。
8. 已完成 M1 第四个 Runtime Event 纵向切片：接入 `approval.requested / resolved / revoked`，审批状态与事件同事务提交，事件不保存审批意见和恢复元数据；审批 HTTP 续跑按 `taskId` 恢复原 Run 作用域，使后续 Step / Tool 事件继续写入原事件序列。
9. 已完成 M1 第五个 Runtime Event 纵向切片：接入 `artifact.created / artifact.published`，从成功步骤 `outputSnapshot.payload.artifacts` 提取产物事实，使用 `taskId + path` 确定性生成稳定 `artifactId`；事件仅保存文件元数据和受保护下载地址，不保存 `contentBase64`，发布事件与最终回复链接共用同一 URL 构建逻辑。
10. 已完成 M1 第六个 Runtime Event 纵向切片：接入 `checkpoint.created / checkpoint.restored`，Checkpoint 快照插入与创建事件同事务提交并映射稳定 `CHECKPOINT_{checkpointId}` Item；事件仅保存步骤位置、Plan ID、恢复入口和安全处置，不复制 Plan、Decision、Step Results 快照正文，恢复事件只在 `task_resume / approval_resume` 实际加载快照时发布。
11. 已完成 M1 第七个 Runtime Event 纵向切片：接入 `plan.created / plan.replanned`，任务首个计划版本发布创建事件，后续自动重规划、Observation 重规划、人工调整、审批拒绝二次规划和无 Checkpoint 恢复计划统一发布重规划事件；使用 `planId` 映射 Envelope `itemId`，事件仅保存版本关系、来源、质量评分和步骤增删改计数，完整计划继续保存在 `task_plans / task_plan_steps`。
12. 已完成 M1 第八个 Runtime Event 纵向切片：前端新增统一 Runtime Event SSE 消费器，按 `sequenceNo` 幂等处理并通过 `Last-Event-ID / afterSequence` 自动断线续传；会话页统一由事件驱动消息增量、执行进度、审批弹窗、产物提示和 Run 终态，连接异常时继续保留普通轮询降级。
13. 已完成 M1 第九个 Runtime Event 纵向切片：锁定 26 个 M1 标准事件编码及历史 `reset / delta / progress` 映射契约，补齐统一 SSE 游标优先级和非法游标校验、旧 SSE 兼容投影测试；新增浏览器级断线重连测试，验证重复事件幂等忽略、`Last-Event-ID / afterSequence` 从已处理序号续传，以及审批、产物和等待终态的前端投影。
14. 已完成 M1 第十个 Runtime Event 纵向切片：新增 `auto / unified / legacy` 前端 Feature Flag、Agent 白名单和按 Agent 稳定百分比灰度；`auto` 模式统一协议连接失败时自动回退旧 Run SSE，旧接口继续作为无数据库回滚的兼容路径；补齐发布回滚手册和浏览器级灰度/自动回退测试，并完成本地 Docker 双后端、前端、Flyway migration 及健康状态发布验证。
15. 已完成 M1 最终审计：新增 `docs/runtime/Runtime状态机.md`，冻结 Run、Task、Step、Approval、Durable Wait、Checkpoint 的关闭态、可恢复挂起态和非法流转；统一 `waiting_event / paused` 在 Run 取消入口的关闭状态语义并补测试；数据库设计补齐复用事件表的所有权隔离边界、未来 RLS 字段与索引，以及外部 Projection Outbox 的状态、幂等和失败处置设计。M1 全部 10 项任务完成，M2 尚未开始。
16. 已完成 M2 第一个可验证 Replay 纵向切片：新增 `prompt_replay / simulation_replay / execution_replay` 稳定模式契约和 `runtime_replays` 独立审计表；现有 Trace Prompt 回放正式生成独立 `replayCode / replayTraceId`，调用 LLM 前冻结安全 Manifest 与不可变 Input，结果不回写原 Run、Task、Trace 或 Artifact；Prompt Replay 固定禁止 Tool、Workflow、网络写、文件写和数据库业务写，并排除模型密钥与基础地址。Simulation Replay 和 Execution Replay 尚未开放。
17. 已完成 M2 第二个 Replay 纵向切片：开放管理员 `simulation_replay` 接口，新增 `ReplayEngine / ReplayModeHandler`，按请求时上界冻结连续 Runtime Event，并以 `step.completed` 关联成功步骤的不可变 `output_snapshot` 和 SHA-256；Simulation Handler 按事件顺序注入录制 Tool/Workflow 结果并重组消息，不依赖 LLM、Tool Provider、Workflow Executor 或 Sandbox，缺少关键事件或快照时明确失败，来源 Run、Task、Step、Trace 和 Artifact 保持不变。
18. 已完成 M2 第三个 Replay 纵向切片：新增结构化 Event Diff，以 `step:{taskStepId}` 和 `event:{sequenceNo}:{eventType}` 对齐来源事实与实际注入观察，对规范排序 JSON 计算 SHA-256，输出 `matched / changed / missing / unexpected`、变化字段和不一致原因；来源消息事件独立重组并与 Replay 结果比较，完整 Diff 写入接口响应和 `runtime_replays.result_snapshot`，不修改任何来源执行数据。
19. 已完成 M2 第四个 Replay 纵向切片：从冻结 `task_steps.output_snapshot` 的 Artifact Base64 内容计算实际大小和 SHA-256，校验快照声明以及 `artifact.created / artifact.published` 的稳定 ID、路径、文件名、大小和媒体类型；Replay Input 冻结不含正文的 `recordedArtifacts`，结果保存结构化 `artifactVerification`，不读取已清理的 Sandbox Workspace，关键内容缺失或损坏时明确失败。
20. 已完成 M2 第五个 Replay 纵向切片：新增管理员 Replay 分页与详情接口和独立管理页，支持按模式、状态、来源 Run 筛选，查看不可变 Manifest、Input、Result、结构化 Event Diff、Artifact 校验与失败原因；页面只开放无副作用 `simulation_replay` 发起入口，`execution_replay` 继续保持未开放。
21. 已完成 M2 第六个 Replay 纵向切片：新增 Replay 管理页浏览器端到端测试，覆盖管理员列表/详情、Event Diff、Artifact 校验、`SUPER_ADMIN` 按冻结事件范围发起 Simulation Replay，以及普通用户前端权限阻断；测试采用固定 HTTP 夹具，不依赖真实 LLM、Tool Provider、Sandbox 或外部数据库。
22. 已完成 M2 第七个 Replay 纵向切片和最终审计：后端 Replay 执行入口支持总开关、Prompt/Simulation 模式开关、Project/Agent 白名单和稳定百分比灰度，成功执行将决策冻结到 Manifest；灰度拒绝发生在快照读取和实例创建前，关闭入口仍保留历史审计查询，`execution_replay` 固定不开放；新增发布回滚手册。M2 全部完成。
23. 已完成 M3 第一个 AgentLoopKernel 纵向切片：新增 `AgentLoopKernel / ExecutionModeStrategy / AgentLoopExecutionGateway` 及旧 Orchestrator Adapter；会话主链通过默认关闭的 Agent 白名单/稳定百分比 Feature Flag 在旧直接路径与 Adapter 路径间路由，两条路径复用同一执行实现，不影子双执行、不改变返回和异常契约；补充规范、测试和发布回滚手册。
24. 已完成 M3 第二个 RuntimeContextProvider 纵向切片：`ExecutionContext.CurrentUserContext` 冻结租户、组织、工作区和 Project 作用域，Kernel 通过 `RuntimeContextProvider` 统一获取作用域与资源标识；灰度新增 Project 白名单和 Project + Agent 稳定分桶，不依赖 ThreadLocal，仍保持旧 Orchestrator 单次执行。
25. 已完成 M3 第三个 RuntimeStepExecutor 纵向切片：复用现有 `ExecutionStepExecutionRequest / Result`，将 Workflow、Approval 和 `ExecutionStepHandlerRegistry` 路由收口到 `LegacyRuntimeStepExecutorAdapter`；Orchestrator 只通过 `RuntimeStepExecutor` 执行单步，Workflow 恢复参数、快照、Approval 异常和 Handler 返回继续原样传递。
26. 已完成 M3 第四个 ObservationPolicy 纵向切片：新增 `ObservationPolicy / ObservationPolicyRequest / LegacyObservationPolicyAdapter`，Orchestrator 不再直接依赖 `ObservationDecisionService`；规则、LLM 和 fallback 决策仍复用原实现，Trace、finish、replan 异常和 Checkpoint 仍保留在循环层。
27. 已完成 M3 第五个 CheckpointStore 纵向切片：新增 `CheckpointStore / CheckpointSaveRequest / LegacyCheckpointStoreAdapter`，Orchestrator 与 Conversation 恢复主链不再直接依赖 `ExecutionCheckpointService`；保存、查询、自动恢复、恢复标记、副作用歧义处置和分支创建均原样委托给现有 PostgreSQL 实现，不改数据库和快照契约。
28. 已完成 M3 第六个 RuntimeEventSink 纵向切片：新增 `RuntimeEventSink / LegacyRuntimeEventSinkAdapter`，统一 Orchestrator 的运行进度、Step 创建/迁移和 Approval 请求事件出口；原事务 Service 继续保证 Step / Approval 状态与 Runtime Event 原子性，Artifact 联动和 Checkpoint Event 归属不变。
29. 已完成 M3 第七个 ApprovalCoordinator 纵向切片：新增 `ApprovalCoordinator / LegacyApprovalCoordinatorAdapter`，统一 Approval 请求、状态迁移和审批续跑 Run Scope；Orchestrator、Conversation 与审批 HTTP 入口不再直接依赖 `ApprovalRuntimeEventService`，审批状态与 Runtime Event 原子性、弹窗交互和原 Run 续跑语义不变。
30. 已完成 M3 第八个 RuntimeResponseAssembler 纵向切片：新增 `RuntimeResponseAssembler / LegacyRuntimeResponseAssemblerAdapter`，统一计划响应汇总、最终回复流式 Scope 和 Artifact 一致性收口；Orchestrator 不再直接依赖具体响应服务，terminal reply 复用、矛盾文案校正、下载链接、published 事件及异常语义保持不变。
31. 已完成 M3 第九个纵向切片和最终审计第一批：新增独立 `RuntimeStepResultContext`，移除 Orchestrator 嵌套步骤结果类型，Observation、Checkpoint、Response、恢复、Workflow 和步骤执行链统一使用新模型；旧 Checkpoint JSON 保持兼容。事件层已确认核心写入、PostgreSQL 实现、统一查询投影和旧 SSE 门面分层；剩余 AgentLoop 请求/结果、单步执行 DTO、Checkpoint 返回模型三类过渡耦合待逐项收口。
32. 已完成 M3 第十个纵向切片和最终审计第二批：新增稳定 `RuntimeExecutionRequest / RuntimeExecutionAggregate`，将 Recovery、Approval Replay 快照迁出 Orchestrator，AgentLoop、Gateway、Checkpoint、Conversation 和 Orchestrator 统一使用独立模型；Feature Flag 两条路径仍单次执行并返回同一聚合结果，等待审批、等待事件和人工暂停语义不变。剩余单步执行 DTO 与 Checkpoint 返回模型两类过渡耦合待收口。
33. 已完成 M3 第十一个纵向切片和最终审计第三批：新增稳定 `RuntimeStepExecutionCommand / RuntimeStepExecutionOutcome`，Plan、Workflow、Step Handler 和 Kernel 统一使用独立单步执行模型，删除步骤 Service 层旧 DTO；Workflow 恢复参数、输出快照、Approval 挂起异常以及普通 Handler 请求和结果对象语义保持不变。剩余仅有 Checkpoint 返回模型待收口。
34. 已完成 M3 第十二个纵向切片和最终审计：新增稳定 `RuntimeCheckpoint / RuntimeCheckpointForkResult`，Checkpoint Store、PostgreSQL 实现、Conversation 恢复链和 HTTP 返回统一使用独立模型，移除 Checkpoint Service 嵌套返回类型；查询、自动恢复、恢复标记、歧义处置、分支创建及历史 JSON 字段保持不变，无需数据库迁移。M3 全部完成。
35. 已完成 M4 第一个 Runtime Plugin 纵向切片：新增 `AgentRuntimePlugin` SPI、不可变 Manifest 与 Dependency 模型、七类组件类型及 Draft 2020-12 JSON Schema；基础契约校验必填字段、SemVer、平台 API 主版本、入口类、重复声明和未知组件类型。当前 Tool Bean 发现链及运行行为不变，不新增数据库，不支持热卸载。
36. 已完成 M4 第二个 Runtime Plugin 纵向切片：新增基于线程上下文 ClassLoader 和 Java `ServiceLoader` 的 `RuntimePluginDiscovery`，`ToolExecutorRegistry` 建立 Manifest Tool 归属映射且继续返回原 Executor 实例；高考插件已增加统一 SPI 入口、Manifest 和 Provider 文件，并通过真实 Jar 验证可发现 5 个 Tool。当前不新增数据库、不改变 Tool 路由与执行语义。
37. 已完成 M4 第三个 Runtime Plugin 纵向切片：新增统一启动校验器和轻量 SemVer 约束匹配器，在组件注册前阻断平台 API 不兼容、入口不一致、插件/组件冲突、Tool 实现缺失或重复、必需依赖缺失、依赖版本不匹配、非法约束和依赖环；错误提供稳定 `errorCode + details`，可选依赖缺失不阻断。无数据库变更。
38. 已完成 M4 第四个 Runtime Plugin 纵向切片：`registry-center` 新增插件定义、不可变 Manifest/制品版本、环境安装替换与启停、Agent 绑定和生命周期审计 API；新增 5 张项目作用域 RLS 表，安装变更统一标记 `pending_restart`，并阻断使用中安装被停用/卸载或对应版本被废弃。
39. 已完成 M4 第五个 Runtime Plugin 纵向切片：`agent-core` 启动后按环境读取 Registry 期望安装，核对本地实际插件编码、版本和可计算的 Jar SHA-256，并回写加载结果；新增插件健康、依赖、组件注册、未登记插件和手动对账观测。Registry 不可用只降低治理状态，不阻断 Core 启动。
40. 已完成 M4 Runtime Plugin 第六个纵向切片与最终审计：本地 Docker 发布链构建并只读挂载真实高考插件 Jar，通过 `PropertiesLauncher loader.path` 装载；修正服务身份加载审计的租户作用域继承，平台 E2E 验证插件定义、版本、环境安装、Jar SHA-256、Core 对账、`loaded` 回写和租户内审计。Runtime Plugin 当前范围完成，下一步进入 Capability Policy。
41. 已完成 M4 Capability Policy 第一个纵向切片：冻结九类 Runtime Capability 与四级 Tool Side Effect；Registry 新增独立声明字段、写入校验和内置 Tool 基线，Agent Tool API/Core 快照贯通新字段，Runtime Plugin Java/Schema/Registry 校验统一收紧，Tool 管理页支持配置。当前声明不授予权限，下一步实现调用前 `CapabilityPolicyEngine`。
42. 已完成 M4 Capability Policy 第二个纵向切片：新增确定性 `CapabilityPolicyEngine`，按用户、Agent、Project、Tool、Capability、Side Effect、参数和目标资源匹配显式策略或安全基线；`ToolRouterService` 在副作用占用和 Provider 调用前强制执行 `allow / sandbox / approval / block` 并发布脱敏 `policy.decided`；Plan/Workflow 审批通过后重试当前节点且不会与前置 Guardrail 循环。下一步统一审批前后的 Tool Call ID，并补独立命中审计查询。
43. 已完成 M4 Capability Policy 第三个纵向切片：首次策略决策预分配 Tool Call ID，并将其与参数哈希写入 Capability 审批元数据及安全 Approval 事件；审批续跑仅在节点和参数哈希一致时复用原 ID，后续 `tool.call.*` 与 `policy.decided` 使用同一调用标识和查询 `itemId`；旧审批记录缺失标识时兼容生成新 ID。下一步补 Capability 全量决策运营审计。
44. 已完成 M4 Capability Policy 第四个纵向切片与当前范围最终审计：复用 `policy.decided` 作为全量决策事实源，新增当前用户隔离的分页、指标、Capability/Tool 聚合和同一 Tool Call 事件链详情 API；前端新增“能力策略审计”页面，只展示安全 Payload 和哈希；事件表增加专用部分索引并经真实 PostgreSQL/Flyway E2E 验证。M4 当前范围完成，下一阶段进入双执行模式。
45. 已完成 M5 双执行模式第一个纵向切片：冻结 Agent `workflow_plan / interactive_loop` 默认与允许模式契约，Registry 和管理前端贯通配置；Core 在 Workflow、Approval、非法配置或动态循环未开放时确定性回退 `workflow_plan`，并发布脱敏 `run.mode.selected` 审计事件。现有 Agent、Kernel 灰度和 Workflow 行为不变；下一步实现 Interactive Loop 预算与停止原因模型。
46. 已完成 M5 双执行模式第二个纵向切片：Agent 管理支持最大迭代、LLM、Tool、Token、成本和重复调用硬预算；Registry 强制字段范围及 Loop/Run LLM 上限关系，Core 新增安全默认策略解析和调用前原子预算状态机，冻结十一类停止原因与脱敏 `run.loop.stopped` 事件。全局 CostGovernance 和共享 LLM 预算保持外层门禁；下一步接入最小 Interactive Loop 控制器。
47. 已完成 M5 双执行模式第三个纵向切片：新增只接受 `tool_call / final_answer` JSON 的 LLM Action 决策器和最小循环控制器，在物理调用前估算并占用 Token/成本预算，限定有效 Tool 范围并以规范参数哈希检测重复；Tool 继续复用现有 Router、Capability、审批、副作用、成本和事件链，失败 Observation 有界且不携带输出快照。生产路由仍关闭；下一步增加灰度 Kernel Adapter，并补审批恢复 Checkpoint。
48. 已完成 M5 双执行模式第四个纵向切片：新增项目作用域 RLS 表 `interactive_loop_checkpoints`，审批挂起前保存预算/指纹、历史 Observation、待执行 Action 与 Capability 审批绑定；审批通过后严格校验节点、Tool Call ID、参数哈希和 Trace，只重放挂起 Tool 且不二次计费，完成后从下一轮继续。生产路由仍关闭；下一步接入 Feature Flag、稳定灰度和 `AgentLoopKernel` Adapter。
49. 已完成 M5 双执行模式第五个纵向切片：新增默认关闭的 Interactive Loop 独立 Feature Flag、Project/Agent 白名单和稳定百分比灰度；`interactive_loop_kernel_v1` Adapter 接入 Gateway，并复用 Task Step、ApprovalCoordinator 与通用 Plan Checkpoint，使对话框审批和既有 `approveTask` 恢复入口保持不变。下一步执行本地小范围真实 E2E 后再扩大灰度。
50. 已完成 M5 双执行模式第六个纵向切片：对白名单测试 Agent 完成真实 LLM Tool Loop、预算停止、Capability 审批弹窗、批准恢复与最终回复 E2E；修复 Catalog 安装和历史 MCP Tool 缺失安全声明导致 `write_file` 被只读基线误放行的问题，Registry 自动推断并迁移回填，Core 对旧快照只提升风险兜底。默认开关及百分比灰度仍关闭；下一步验证异步 Run、流式事件和取消边界。
51. 已完成 M5 双执行模式第七个纵向切片：异步审批恢复后的 Run 状态迁移与新终态事件改为同事务提交，并对重复同步去重，使 SSE/Replay 可从 `run.waiting` 游标继续读取 `approval.resolved -> run.completed`；Interactive Loop 增加线程中断和持久化取消标记的协作式门禁，真实运行中取消在第 0 次迭代停止且未产生任何 Tool 调用，Task/Run 分别进入 `cancelled`，取消不触发重试。下一步完成灰度指标、回退开关与运维 Runbook 验收。
52. 已完成 M5 双执行模式第八个纵向切片及当前范围收口：新增优先级最高的 `CORE_INTERACTIVE_LOOP_KILL_SWITCH`，灰度路由和 Loop 停止接入低基数 Micrometer 指标，Actuator 开放需认证的 `metrics` 端点；新增 Interactive Loop 发布回滚 Runbook，冻结灰度批次、量化门禁、SQL 审计、紧急熔断和恢复原则。M5 当前范围已完成，默认仍不扩大灰度。
53. 已完成 Runtime SDK 与宿主集成第一个纵向切片：新增 TypeScript `RuntimeClient / RuntimeSdkError` 和稳定 Thread/Run 类型，统一 `baseUrl`、动态 JWT、HTTP/业务错误、超时、URL 编码与自动 `Idempotency-Key`；会话页 Thread 创建/查询和异步 Run 创建/查询/取消已通过 SDK 调用，保持原页面签名与状态逻辑；同步补齐 Core/Registry 的本地 Playwright Origin。下一步将 Runtime Event SSE 和断线续传收口到 SDK。
54. 已完成 Runtime SDK 与宿主集成第二个纵向切片：新增 `RuntimeClient.streamEvents`，统一 Runtime Event 路径、动态 JWT、`runCode` 编码、总超时和主动取消；通过 `afterSequence + Last-Event-ID` 双游标、`sequenceNo` 去重和非终态自动重连保证断线续传，消息、进度、审批、产物和终态投影下沉 SDK；原事件入口保留为兼容适配层，灰度及旧 SSE 回退不变，并补齐流式稳定错误码与浏览器回归。下一步接入 Approval 与任务恢复 API。
55. 已完成 Runtime SDK 与宿主集成第三个纵向切片：新增 `resumeTask / approveTask / rejectTask / revokeApproval / signalWait`，统一失败 Task 恢复、审批通过/驳回/撤回和持久化等待事件唤醒路径；ID 与 `eventKey` 统一校验编码，拒绝支持二次规划，审批和恢复保持 10 分钟长请求窗口；现有对话审批弹窗和会话 API 签名不变，内部已委托 SDK。下一步接入 Artifact 下载和 Replay API。
56. 已完成 Runtime SDK 与宿主集成第四个纵向切片：新增 `downloadArtifact`，返回宿主无关的 Blob/文件名/类型/大小并按 RFC 5987 解析 UTF-8 文件名，中文及特殊字符路径统一编码；新增 Replay 分页、详情和 Simulation Replay API、稳定类型、分页与事件范围校验；会话下载和 Replay 页面原函数签名不变并已委托 SDK。TypeScript SDK 当前计划能力完成，下一步建立 Java SDK Thread/Run 基座。
57. 已完成 Runtime SDK 与宿主集成第五个纵向切片：新增独立 `agent-runtime-sdk-java` Maven 模块，使用 Java 17 `HttpClient` 和可替换 `RuntimeHttpTransport`，实现 Thread 创建/查询与 Run 创建/查询/取消；统一动态 JWT、UUID 幂等键、优先级、URL 编码、超时和 `RuntimeSdkException` 错误契约，且不引入 Spring Boot。下一步补齐 Java Runtime Event、Approval、Recovery、Artifact 和 Replay。
58. 已完成 Runtime SDK 与宿主集成第六个纵向切片：Java Transport 增加 SSE/二进制流扩展，新增阻塞式 `streamEvents`，支持双游标续传、序号去重、总超时、空连接保护和线程中断，并投影消息、进度、审批、产物与 Run 终态；补齐 Approval、失败/等待恢复、Artifact 下载及 Replay 全生命周期，统一长请求、UTF-8 文件名、分页和事件范围校验。下一步增加 Java 示例应用、契约测试与阶段发布验收。
59. 已完成 Runtime SDK 与宿主集成第七个纵向切片及阶段收口：新增独立 Java 17 示例模块和可执行 Fat Jar，可创建 Thread/Run、消费 SSE 并在审批挂起后沿原 Task 继续；真实 JDK HTTP/SSE Contract Test 覆盖 JWT、幂等、优先级、双游标和审批恢复，平台 E2E 在隔离双服务环境实际运行示例并验证 Run 完成、Task 关联和事件终态。M5 双执行模式与 SDK 当前范围全部完成。
60. 已完成 M6 图片能力全部六个纵向切片：建立会话附件存储、授权接口、消息/异步 Run 引用恢复、供应商无关多模态模型协议与模型输入模态配置；新增独立 `image-understanding-plugin`，通过当前消息 `attachmentId` 提供本地版面分析和可配置 OCR Provider；最终回复确定性选择视觉模型原图直读或已授权 OCR Tool，能力不足时返回可执行配置提示；`input.attachment.resolved / analyzed` 冻结不含正文的引用与 SHA-256 并进入 Simulation Replay。隔离平台 E2E 已验证双插件、multipart 上传、异步图片 Run、多模态 Provider、持久化事件无 Base64、18 个 Task 和 Java SDK 主链，M6 当前范围全部完成。
61. 已修复 JWT 失效被 Spring Security 默认返回为 HTTP 403、前端 Runtime 只展示失败的问题：Registry/Core 统一通过 JSON AuthenticationEntryPoint 返回 `HTTP 401 + code=401`；axios、Runtime SDK 和 Runtime Event SSE 统一清理 Token 并跳转登录页，登录成功后恢复原站内地址；真正的权限不足 403 不触发退出。后端响应契约、前端构建和 Chromium 跳转用例均已通过。

## 2026-11-25 GitHub 发布准备

本轮目标：清理源码快照中的共享默认凭据、完善本地初始化和发布说明，保留双服务边界及已有数据库兼容性。

已落地：

1. 双服务数据库密码、JWT、服务 Token、Webhook 密钥与凭据加密密钥改为环境变量必填，清理 Java 配置类和部署脚本默认值；CORS Origin 可配置并移除本机局域网地址。
2. 移除登录页演示账号密码展示及预填；新增安全迁移锁定原始默认密码管理员，并通过环境变量一次性初始化，已改密码账号保持原状。
3. 增加空值 `.env.example`、随机配置工具与本地 PostgreSQL/pgvector Compose；拒绝覆盖已有 `.env`，明确升级保留旧加密密钥。
4. 完善 Git/Docker 忽略规则、LF 约定、README、相对文档链接、安全说明、Apache-2.0 LICENSE / NOTICE 及 GitHub 发布清单。
5. 增加普通文件与 Git 暂存版本凭据扫描、Python 安全工具回归；现有 CI 和平台 E2E 接入安全配置与显式登录。
6. 已同步 `docs/数据库设计.md` 的账号状态、metadata 标记和初始化规则，不修改既有 Flyway 迁移。

验收状态：本轮发布准备范围已完成。实际验证结果：

1. 凭据扫描通过：1143 个预计发布文本文件，未发现匹配项；扫描不覆盖数据库、服务器环境或二进制附件正文。
2. Registry 50 项测试全部通过，包含 5 项管理员初始化测试；Java SDK 6 项测试通过；Python 发布工具 6 项回归通过，包含隔离 Git 暂存索引和忽略规则检查。
3. 前端 TypeScript 检查与生产构建通过。使用系统 Chrome 无头验证用户名/密码为空、无演示密码提示、Token 失效清理和登录后回跳地址保留。
4. 隔离 PostgreSQL 13 验证新增迁移的 4 类账号状态规则通过；51 个原有 Flyway 迁移 SHA-256 保持不变。
5. Bash 语法、YAML 解析、README 本地链接和已修改文件 UTF-8 无 BOM / LF 检查通过。
6. Windows 上全量 `mvn test` 在 Core 的 345 项测试中出现 2 个 assertion failure 和 1 个 error，集中在未修改的 `DockerSandboxExecutorPathTest`、`SandboxWorkspaceServiceTest`：分别为 POSIX 路径的 Windows 盘符差异和工作区写权限设置失败。未将全量测试记为通过；完整 Linux / Docker 平台 E2E 需要在对应环境执行，本机没有 Docker，未运行。
7. Playwright 默认 Chromium 缓存缺失；已用系统 Chrome 完成页面行为验证，不将默认 Playwright 命令记录为通过。完整平台脚本继续使用其标准 Chromium 安装步骤。

本轮无业务接口结构或旧迁移 checksum 偏差；保留上述环境验证限制。未创建远程仓库、未上传代码、未恢复旧 Git 历史或 `.github`，也未连接或修改既有业务数据库。
