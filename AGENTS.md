# AGENTS.md

请始终使用中文与我交流，但保留必要的英文技术名词、类名、方法名、字段名、命令、错误信息和日志原文。

## 1. 项目定位

这是一个通用 Agent 平台项目，不是单一业务 Agent。

当前采用双服务架构：

1. `agent-core`
2. `registry-center`

另有一个统一前端：

1. `frontend-web`

## 2. 双服务边界

### 2.1 agent-core

`agent-core` 负责运行时执行，主要包含：

1. 会话管理
2. 上下文构建
3. 决策与规划
4. 任务拆解与执行
5. 工作流调度
6. 工具路由与调用
7. 沙盒执行协调
8. 反思、恢复、重试
9. 记忆沉淀
10. 响应生成
11. 运行日志与状态追踪

### 2.2 registry-center

`registry-center` 负责注册、配置、发布、发现，主要包含：

1. Agent 注册与版本管理
2. Tool 注册与发布
3. Workflow 定义与版本管理
4. Prompt 模板管理
5. Knowledge 元数据管理
6. Sandbox 配置管理
7. 用户、角色、权限管理
8. 资源发现、启停、上下线
9. 元数据与依赖关系管理

### 2.3 边界原则

1. 描述“资源长什么样、怎么配置、哪个版本可用”的内容，放 `registry-center`
2. 描述“资源怎么被执行、执行到哪一步、执行结果如何”的内容，放 `agent-core`
3. 不要把两个服务做成一个大单体后期再硬拆
4. 当前阶段只拆这两个服务，不继续细拆更多微服务

## 3. Workflow 约定

1. Workflow 的编排定义放 `registry-center`
2. Workflow 的执行实例放 `agent-core`
3. 第一阶段节点可以以 `tool` 节点为主
4. 但不要把节点模型写死为只能支持 Tool
5. 至少预留 `tool`、`condition`、`approval`、`start`、`end` 这些节点类型的扩展能力
6. 节点模型不要和 Tool 实体硬编码绑定
7. 当节点类型为 `tool` 时，再关联具体 `tool_code`

## 4. 认证与权限约定

1. 登录认证、用户管理、角色管理、权限配置、Token 签发统一放 `registry-center`
2. `agent-core` 负责校验 Token，并执行运行时鉴权
3. 不要两个服务分别维护独立账号体系
4. 不要让 `agent-core` 再单独签发一套业务 Token
5. 建议统一使用 `JWT`
6. `agent-core` 本地完成 Token 校验，不要每次请求都远程回源校验

## 5. 技术栈约定

### 5.1 前端

1. `Vue 3`
2. `TypeScript`
3. `Vite`
4. `Pinia`
5. `Vue Router`
6. `Element Plus`
7. `axios`

### 5.2 后端

1. `Java 17`
3. `Spring Boot 3.x`
4. `Spring Web`
5. `Spring Validation`
6. `Spring Security`
7. `MyBatis-Plus`
8. `Spring Data Redis`
9. `LangChain4j`

### 5.3 数据与基础设施

1. 主数据库：`PostgreSQL`
2. 向量检索：`pgvector`
3. 缓存：`Redis`
4. 沙盒：`Docker Sandbox`
5. 本地编排：`Docker Compose`

## 6. 目录结构约定

项目根目录主要包含：

1. `agent-core`
2. `registry-center`
3. `frontend-web`
4. `docs`
5. `docker`
6. `scripts`

说明：

1. 单仓库管理多个服务
2. 两个后端服务必须独立启动
3. 前端当前为统一管理后台

## 7. 数据库归属约定

1. 两个服务不共用核心业务表
2. 不允许跨服务直接写对方数据库
3. 跨服务取数走 API，不走直连数据库

归属原则：

1. `registry-center` 管资源定义、版本、权限、发布
2. `agent-core` 管会话、任务、执行、日志、审批、记忆、运行状态

## 8. 编码格式要求

1. 所有源码、配置、SQL、Markdown 文档统一使用 `UTF-8`
2. 新增或修改文件优先使用 `UTF-8 without BOM`
3. 换行符统一使用 `LF`
4. 不要混用 `CRLF` 和 `LF`
5. 中文注释、中文日志、中文提示必须保证不乱码

## 9. 编码风格要求

### 9.1 通用要求

1. 先看现有代码风格，再改代码
2. 优先最小改动，不做无必要的大重构
3. 不要擅自删除已有逻辑
4. 不要擅自修改公共方法签名
5. 不要擅自改接口返回结构
6. 不要随意新增复杂抽象层
7. 不要随意新增依赖，确需新增时先说明原因

### 9.2 Java 要求

1. 类名使用 `UpperCamelCase`
2. 方法名、字段名使用 `lowerCamelCase`
3. 常量使用 `UPPER_SNAKE_CASE`
4. 包名全小写
5. 方法职责尽量单一，不要过长
6. 超过 4 到 5 个参数时优先封装 DTO
7. 避免深层嵌套 `if/else`
8. `BigDecimal` 计算注意精度、舍入和 `null` 判断
9. MyBatis-Plus 优先使用 `LambdaQueryWrapper` / `LambdaUpdateWrapper`

### 9.3 前端要求

1. 页面级组件放 `views`
2. 可复用组件放 `components`
3. 接口请求统一放 `api`
4. 类型定义统一放 `types`
5. 不要把请求、类型、页面逻辑全部堆在一个 `.vue` 文件中
6. 注意响应式、类型定义和接口返回结构

## 10. 注释要求

1. 每个 `public` 方法建议加注释
2. 核心业务方法必须加注释
3. 复杂逻辑、状态流转、分支判断、重试逻辑、路由逻辑必须加注释
4. 注释重点写“为什么这么做”，不要只重复代码表面含义
5. 类注释要说明职责边界
6. `model`、`vo`、`dto` 中的字段必须加注释
7. 对于 `record` 类型，字段说明可通过字段前注释或类注释中的 `@param` 明确标注

## 11. 数据库命名规范

1. 表名统一小写下划线 `snake_case`
2. 字段名统一小写下划线
3. 主键统一 `id`
4. 创建时间统一 `created_at`
5. 更新时间统一 `updated_at`
6. 创建人统一 `created_by`
7. 更新人统一 `updated_by`
8. 状态字段统一 `status`
9. 删除标记统一 `deleted` 或 `is_deleted`
10. 扩展字段统一 `metadata` 或 `extra`

## 12. 接口规范

1. 接口统一使用 `/api` 前缀
2. 列表、详情、创建、更新、删除职责要清晰
3. 查询接口和执行动作接口分开
4. 入参使用 DTO，不直接暴露实体
5. 所有接口统一参数校验

建议统一返回结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 13. 日志与异常

1. 统一使用 `Slf4j`
2. 会话创建、任务规划、工作流执行、工具调用、沙盒执行、审批动作、重试恢复等关键流程必须打日志
3. 不要在日志中输出密码、Token、密钥等敏感信息
4. 全局异常统一处理
5. 业务异常和系统异常分开
6. 返回给前端的错误信息要可读，但不要暴露堆栈

## 14. 开发流程要求

1. 先给结论，再给步骤
2. 复杂任务先给简短计划，再开始改代码
3. 修改代码前，优先阅读相关文件和现有风格
4. 修改后说明影响范围和验证方式
5. 尽量给出启动命令、测试命令、`curl` 示例或页面检查点
6. 给命令时说明在哪个目录执行
7. macOS、Linux、Windows / PowerShell 命令要区分，不要混用
8. 所有 Git 提交信息统一使用中文，不使用英文 commit message
9. 用户说“提交”时，默认理解为执行 `git add`、`git commit`、`git push`
10. 所有开发任务完成后，必须检查项目计划是否已完成、是否有偏差，并及时更新计划文档
11. 所有涉及表设计、字段、索引、关系、状态规则的修改，必须同步更新 `docs/数据库设计.md`

## 15. 当前项目特别注意点

1. `agent-core` 和 `registry-center` 是两个独立服务
2. Workflow 编排定义在 `registry-center`，执行在 `agent-core`
3. Token 签发和权限源头在 `registry-center`
4. 运行时权限判断在 `agent-core`
5. 不要把 Tool、Workflow、Prompt 硬编码在代码里，优先配置化
6. 不要把 `Memory` 和 `Context` 混成一个大字段
7. 所有 Git 提交说明统一使用中文
8. `Tool` 资源采用 `tool_type + tool_category` 双维度分类
9. `Workflow` 资源采用 `workflow_type + workflow_category` 双维度分类
10. `Prompt` 资源采用 `prompt_type + prompt_category` 双维度分类
11. Agent Tool 范围当前固定支持 `inherit_default`、`inherit_and_append`、`custom`
12. 资源分类、权限等级、范围模式等可枚举字段应优先纳入平台字典管理，前端优先使用下拉框而不是自由输入
13. `ExecutionContext` 定义为一次 Agent 执行所需全部运行时信息的统一上下文对象
14. `ExecutionContext` 负责汇总 Agent、Prompt、Workflow、可用 Tool、会话、当前消息、Task、Step、历史执行记录等执行现场数据
15. `ExecutionContext` 在实现上必须按信息来源拆分为独立解析方法，不要在一个超长方法里一次性拼装全部数据
16. 开发执行引擎时，优先按“用户输入 -> 上下文构建 -> 路由/规划 -> Task/Step 执行 -> 结果回写”的执行流程推进，而不是优先堆具体业务 Tool

## 16. 多 Agent 协作约定

项目允许采用多 Agent 协作，但必须遵守以下规则：

1. 总计划由主控 Agent 负责，不交给单一专业 Agent
2. 专业 Agent 只负责本角色范围内的分计划、分析和产出
3. 不要让多个 Agent 同时修改同一批文件
4. 产品、DB、开发、测试职责必须分开，不要互相越界
5. 所有角色都要遵守本文件和 `docs/双服务开发约定.md`
6. 每次开发任务结束后，主控 Agent 必须检查并更新项目计划文档
7. 任何涉及数据库表设计的调整，主控 Agent 和 DBAgent 都必须同步更新 `docs/数据库设计.md`
8. `worker` 只按需启用，不长期常驻
9. 每个 `worker` 必须有明确任务边界、输入和输出
10. 同一批次内，未分配明确任务的 `worker` 不应保留
11. `worker` 任务完成后应及时关闭；只有确定马上复用时才短暂保留
12. 多 `worker` 并行时，必须避免修改同一批文件

当前建议角色：

1. `主控Agent`
2. `产品Agent`
3. `DBAgent`
4. `开发Agent`
5. `测试Agent`

计划职责：

1. `主控Agent` 负责总计划、阶段目标、里程碑、串并行安排、最终裁决
2. `产品Agent` 负责需求拆分、业务优先级建议、页面原型说明、验收标准
3. `DBAgent` 负责表设计分计划、数据约束、索引和 SQL 设计建议
4. `开发Agent` 负责技术实现分计划、代码落地和验证方式
5. `测试Agent` 负责测试计划、测试点、风险点、缺陷列表

多 Agent 协作细则和角色说明见：

1. `docs/collaboration/多Agent协作流程.md`
2. `docs/collaboration/产品Agent说明.md`
3. `docs/collaboration/DBAgent说明.md`
4. `docs/collaboration/开发Agent说明.md`
5. `docs/collaboration/测试Agent说明.md`
6. `docs/MVP开发计划.md`

## 17. 如果发现不确定项

1. 明确说明不确定，不要编造
2. 优先采用改动最小、风险最低的方案
3. 如果边界冲突，优先遵守本文件中的双服务职责划分
