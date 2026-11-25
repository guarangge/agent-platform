# registry-center 并行开发计划

## 1. 结论

当前阶段允许前后端并行开发，但范围只限定在 `registry-center` 的 P0 模块。

本轮并行开发目标：

1. 完成认证登录基础能力
2. 完成用户管理基础能力
3. 完成角色管理基础能力
4. 完成用户绑定角色基础能力
5. 完成角色绑定 Agent 基础能力
6. 完成对应前端页面骨架和首轮联调准备

当前原则：

1. 只围绕 `registry-center` 并行
2. 不提前展开 `agent-core`
3. 不提前展开 Tool / Workflow / Prompt 全量联调
4. 前端开发必须以当前接口清单为边界
5. 本计划执行过程中如涉及表设计调整，必须同步更新 `docs/数据库设计.md`

---

## 2. 本轮范围

### 2.1 后端范围

本轮后端只做以下模块：

1. `auth`
2. `user`
3. `role`
4. `user-role`
5. `role-agent`
6. `agent`
7. `tool`
8. `workflow`
9. `prompt`
10. 资源分类与 Agent Tool 范围规则修正
11. 平台资源字典管理与前端下拉改造

### 2.2 前端范围

本轮前端只做以下页面和模块：

1. 登录页
2. 用户管理页
3. 角色管理页
4. 权限管理页骨架
5. Agent 管理页骨架
6. 基础菜单和路由守卫
7. Tool 管理页
8. Workflow 管理页
9. Prompt 管理页
10. Agent Tool 三态范围维护页
11. 字典管理页

### 2.3 本轮不做

本轮不进入：

1. `agent-core` 会话接口开发
2. 会话页联调
3. 复杂权限模型

---

## 3. 后端任务清单

### 3.1 认证模块

状态：已完成并切换真实库

任务：

1. 搭建 `Spring Security` 基础配置
2. 实现登录接口
3. 实现当前登录用户接口
4. 明确 `JWT Token` 生成与校验规则
5. 预留退出登录和刷新 Token 接口骨架

交付物：

1. `auth` 模块代码骨架
2. 登录接口
3. 当前用户接口
4. 认证配置说明
5. PostgreSQL 真实库接入

### 3.2 用户模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现用户分页查询
2. 实现用户详情
3. 实现新增用户
4. 实现编辑用户
5. 实现启用 / 停用用户
6. 实现重置密码

交付物：

1. `users` 相关接口
2. 用户模块基础校验
3. 验证方式说明

### 3.3 角色模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现角色分页查询
2. 实现角色详情
3. 实现新增角色
4. 实现编辑角色
5. 实现启用 / 停用角色

交付物：

1. `roles` 相关接口
2. `menu_scope` 基础处理逻辑

#### 当前已完成范围

1. 已实现角色分页查询接口
2. 已实现角色详情接口
3. 已实现新增角色接口
4. 已实现编辑角色接口
5. 已实现启用 / 停用角色接口
6. 已完成 `menu_scope` 的 JSONB 读写处理

### 3.4 用户绑定角色模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现查询用户已绑定角色
2. 实现绑定角色
3. 实现解绑角色
4. 或者采用覆盖式更新用户角色关系

交付物：

1. `users/{userId}/roles` 相关接口
2. 幂等处理说明

#### 当前已完成范围

1. 已实现 `GET /api/registry/users/{userId}/roles` 查询用户已绑定角色
2. 已实现 `PUT /api/registry/users/{userId}/roles` 覆盖式更新用户角色关系
3. 已完成用户、角色存在性校验
4. 已完成关系差量更新和逻辑删除处理

### 3.5 角色绑定 Agent 模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现查询角色已绑定 Agent
2. 实现绑定 Agent
3. 实现解绑 Agent
4. 或者采用覆盖式更新角色 Agent 关系

交付物：

1. `roles/{roleId}/agents` 相关接口
2. 关系约束处理说明

#### 当前已完成范围

1. 已实现 `GET /api/registry/roles/{roleId}/agents` 查询角色已绑定 Agent
2. 已实现 `PUT /api/registry/roles/{roleId}/agents` 覆盖式更新角色 Agent 关系
3. 已完成角色、Agent 存在性校验
4. 已完成关系差量更新和逻辑删除处理

### 3.6 Agent 模块

状态：已完成并进入 agent-core 阶段前收尾

任务：

1. 实现 Agent 分页查询
2. 实现 Agent 详情
3. 实现新增 Agent
4. 实现编辑 Agent
5. 实现启用 / 停用 Agent
6. 实现 Agent Tool 范围维护

交付物：

1. `agents` 相关接口
2. `agents/{agentId}/tools` 相关接口

#### 当前已完成范围

1. 已实现 `GET /api/registry/agents` 分页查询
2. 已实现 `GET /api/registry/agents/{agentId}` 详情查询
3. 已实现 `POST /api/registry/agents` 新增 Agent
4. 已实现 `PUT /api/registry/agents/{agentId}` 编辑 Agent
5. 已实现 `PUT /api/registry/agents/{agentId}/enable` 启用 Agent
6. 已实现 `PUT /api/registry/agents/{agentId}/disable` 停用 Agent
7. 已实现 `GET /api/registry/agents/{agentId}/tools` 查询 Agent Tool 范围
8. 已实现 `PUT /api/registry/agents/{agentId}/tools` 覆盖更新 Agent Tool 范围
9. 已补 `tools`、`agent_tool_rel` 初始化 SQL 与演示 Tool 数据
10. 已支持 Agent Tool 三态范围：`inherit_default`、`inherit_and_append`、`custom`
11. 已优化 Agent Tool 范围维护弹层的布局、信息层级和滚动展示
12. 已增强 Tool 类型与分类在 Tool 管理页和 Agent Tool 选择弹层中的可见性
13. 已补资源字典规约，待接入统一字典管理和下拉改造
14. 已完成字典类型与字典项管理后端接口
15. 已完成字典管理前端页面
16. 已完成 Tool / Workflow / Prompt / Agent 相关字典下拉接入
17. 已完成 Agent 的默认 Prompt 绑定与 Tool 范围维护
18. 已改造 Agent Workflow 绑定为可用集合维护，去除默认 Workflow 语义，并新增 Agent Workflow 范围接口

### 3.7 Tool 模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现 Tool 分页查询
2. 实现 Tool 详情
3. 实现新增 Tool
4. 实现编辑 Tool
5. 实现启用 / 停用 Tool
6. 实现 Schema 与 Metadata 的 JSON 校验

交付物：

1. `tools` 相关接口
2. Tool 管理页
3. 真实 PostgreSQL 联调结果

#### 当前已完成范围

1. 已实现 `GET /api/registry/tools` 分页查询
2. 已实现 `GET /api/registry/tools/{toolId}` 详情查询
3. 已实现 `POST /api/registry/tools` 新增 Tool
4. 已实现 `PUT /api/registry/tools/{toolId}` 编辑 Tool
5. 已实现 `PUT /api/registry/tools/{toolId}/enable` 启用 Tool
6. 已实现 `PUT /api/registry/tools/{toolId}/disable` 停用 Tool
7. 已完成 `inputSchema`、`outputSchema`、`metadata` 的 JSON object 校验与 JSONB 持久化
8. 已完成真实 PostgreSQL 联调验证
9. 已补充 `toolCategory` 分类维度，支持平台默认 Tool 和行业 Tool 区分

### 3.8 Workflow 模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现 Workflow 分页查询
2. 实现 Workflow 详情
3. 实现新增 Workflow
4. 实现编辑 Workflow
5. 实现发布 Workflow
6. 实现停用 Workflow
7. 实现 `definitionJson` 与 `metadata` 的 JSON 校验

交付物：

1. `workflows` 相关接口
2. Workflow 管理页
3. 真实 PostgreSQL 联调结果

#### 当前已完成范围

1. 已实现 `GET /api/registry/workflows` 分页查询
2. 已实现 `GET /api/registry/workflows/{workflowId}` 详情查询
3. 已实现 `POST /api/registry/workflows` 新增 Workflow
4. 已实现 `PUT /api/registry/workflows/{workflowId}` 编辑 Workflow
5. 已实现 `PUT /api/registry/workflows/{workflowId}/publish` 发布 Workflow
6. 已实现 `PUT /api/registry/workflows/{workflowId}/disable` 停用 Workflow
7. 已完成 `definitionJson`、`metadata` 的 JSON object 校验与 JSONB 持久化
8. 已补 `workflows` 初始化 SQL 与演示 Workflow 数据
9. 已完成真实 PostgreSQL 联调验证
10. 已补充 `workflowType`、`workflowCategory` 分类维度

### 3.9 Prompt 模块

状态：已完成第一批并完成真实库验证

任务：

1. 实现 Prompt 分页查询
2. 实现 Prompt 详情
3. 实现新增 Prompt
4. 实现编辑 Prompt
5. 实现启用 Prompt
6. 实现停用 Prompt
7. 实现 `metadata` 的 JSON 校验

交付物：

1. `prompts` 相关接口
2. Prompt 管理页
3. 真实 PostgreSQL 联调结果

#### 当前已完成范围

1. 已实现 `GET /api/registry/prompts` 分页查询
2. 已实现 `GET /api/registry/prompts/{promptId}` 详情查询
3. 已实现 `POST /api/registry/prompts` 新增 Prompt
4. 已实现 `PUT /api/registry/prompts/{promptId}` 编辑 Prompt
5. 已实现 `PUT /api/registry/prompts/{promptId}/enable` 启用 Prompt
6. 已实现 `PUT /api/registry/prompts/{promptId}/disable` 停用 Prompt
7. 已完成 `metadata` 的 JSON object 校验与 JSONB 持久化
8. 已补 `prompts` 初始化 SQL 与演示 Prompt 数据
9. 已完成真实 PostgreSQL 联调验证
10. 已补充 `promptCategory` 分类维度

---

## 4. 前端任务清单

### 4.1 登录页

状态：已完成第一批

任务：

1. 完成登录页面结构
2. 接入登录接口
3. 保存登录态
4. 完成未登录跳转逻辑

交付物：

1. 登录页
2. 登录态基础处理
3. 路由守卫初版

### 4.2 用户管理页

状态：已完成第一批并完成首轮联调

任务：

1. 完成用户列表页骨架
2. 完成新增 / 编辑弹层
3. 预留绑定角色入口
4. 接入分页查询和详情接口

交付物：

1. 用户管理页
2. 用户表单弹层

#### 当前已完成范围

1. 已接入用户分页查询接口
2. 已接入用户详情接口
3. 已接入新增用户接口
4. 已接入编辑用户接口
5. 已接入启用 / 停用用户接口
6. 已接入重置密码接口
7. 已接入绑定角色弹层
8. 已接入用户已绑定角色查询
9. 已接入覆盖式更新用户角色关系

### 4.3 角色管理页

状态：已完成第一批并完成首轮联调

任务：

1. 完成角色列表页骨架
2. 完成新增 / 编辑弹层
3. 预留绑定 Agent 入口
4. 接入分页查询和详情接口

交付物：

1. 角色管理页
2. 角色表单弹层

#### 当前已完成范围

1. 已接入角色分页查询接口
2. 已接入角色详情接口
3. 已接入新增角色接口
4. 已接入编辑角色接口
5. 已接入启用 / 停用角色接口
6. 已接入绑定 Agent 弹层
7. 已接入角色已绑定 Agent 查询
8. 已接入覆盖式更新角色 Agent 关系

### 4.5 Tool 管理页

状态：已完成第一批并完成首轮联调

任务：

1. 完成 Tool 列表页
2. 完成 Tool 详情抽屉
3. 完成新增 / 编辑弹层
4. 接入启用 / 停用动作
5. 处理 Schema 与 Metadata 的 JSON 文本编辑

交付物：

1. Tool 管理页
2. Tool 表单弹层

#### 当前已完成范围

1. 已接入 Tool 分页查询接口
2. 已接入 Tool 详情接口
3. 已接入新增 Tool 接口
4. 已接入编辑 Tool 接口
5. 已接入启用 / 停用 Tool 接口
6. 已完成 `inputSchema`、`outputSchema`、`metadata` 的 JSON 文本编辑与校验

### 4.6 Workflow 管理页

状态：已完成第一批并完成首轮联调

任务：

1. 完成 Workflow 列表页
2. 完成 Workflow 详情抽屉
3. 完成新增 / 编辑弹层
4. 接入发布 / 停用动作
5. 处理 `definitionJson` 与 `metadata` 的 JSON 文本编辑

交付物：

1. Workflow 管理页
2. Workflow 表单弹层

#### 当前已完成范围

1. 已接入 Workflow 分页查询接口
2. 已接入 Workflow 详情接口
3. 已接入新增 Workflow 接口
4. 已接入编辑 Workflow 接口
5. 已接入发布 / 停用 Workflow 接口
6. 已完成 `definitionJson`、`metadata` 的 JSON 文本编辑与校验

### 4.7 Prompt 管理页

状态：已完成第一批并完成首轮联调

任务：

1. 完成 Prompt 列表页
2. 完成 Prompt 详情抽屉
3. 完成新增 / 编辑弹层
4. 接入启用 / 停用动作
5. 处理 `metadata` 的 JSON 文本编辑

交付物：

1. Prompt 管理页
2. Prompt 表单弹层

#### 当前已完成范围

1. 已接入 Prompt 分页查询接口
2. 已接入 Prompt 详情接口
3. 已接入新增 Prompt 接口
4. 已接入编辑 Prompt 接口
5. 已接入启用 / 停用 Prompt 接口
6. 已完成 `metadata` 的 JSON 文本编辑与校验

### 4.4 权限管理页骨架

任务：

1. 按已确认原型实现“用户管理 / 角色管理”两层结构
2. 用户详情里预留绑定角色区
3. 角色详情里预留绑定 Agent 区
4. 先不做复杂菜单权限页

交付物：

1. 权限管理页骨架
2. 绑定区基础交互占位

### 4.5 Agent 管理页骨架

任务：

1. 完成 Agent 列表页骨架
2. 完成新增 / 编辑弹层
3. 预留 Tool 范围设置区
4. 预留“进入会话”按钮，但本轮不接 `agent-core`

交付物：

1. Agent 管理页骨架
2. Agent 表单弹层

---

## 5. 并行边界

### 5.1 可以并行

1. 后端认证模块与前端登录页可以并行
2. 后端用户 / 角色接口与前端列表页骨架可以并行
3. 前端可以先做静态骨架，再逐步接接口

### 5.2 不建议并行

1. `agent-core` 与当前 `registry-center` P0 不要并行展开
2. Tool / Workflow / Prompt 不要与当前 P0 同时大规模开发
3. 未稳定接口不要直接进入完整联调

### 5.3 依赖关系

1. 前端登录联调依赖后端登录接口稳定
2. 用户、角色、权限页面联调依赖对应分页与详情接口稳定
3. Agent 管理页联调依赖 Agent 接口稳定
4. `agent-core` 开发依赖当前用户、角色、Agent 权限模型稳定

---

## 6. 联调顺序建议

建议按下面顺序联调：

1. 登录页 + 登录接口
2. 当前用户接口 + 路由守卫
3. 用户管理页 + 用户接口
4. 角色管理页 + 角色接口
5. 用户绑定角色接口 + 用户详情绑定区
6. 角色绑定 Agent 接口 + 角色详情绑定区
7. Agent 管理页 + Agent 接口

---

## 7. 完成标准

本轮完成的判断标准：

1. 登录页可正常登录并拿到 Token
2. 页面刷新后可恢复登录态或正确回到登录页
3. 用户管理页可完成列表、详情、新增、编辑、启停
4. 角色管理页可完成列表、详情、新增、编辑、启停
5. 用户可绑定角色
6. 角色可绑定 Agent
7. Agent 管理页可完成列表、详情、新增、编辑、启停
8. 前端页面与后端接口完成首轮联调

---

## 8. 当前状态

状态：已完成

已具备开始条件：

1. 原型已修正
2. 数据库设计已完成初稿
3. `registry-center` 接口清单已完成初稿

当前已完成：

1. `registry-center` 已补基础分层和 `auth` 模块骨架
2. 后端已完成登录接口与当前用户接口
3. 后端已完成 `JWT Token` 生成、校验和基础安全配置
4. 前端已完成登录页
5. 前端已完成登录态保存和基础路由守卫
6. 已完成后端编译、前端构建，以及登录 / `me` 接口验证
7. 认证模块已切换到真实 `PostgreSQL`
8. 已补第一批初始化 SQL 脚本
9. 已在本机 `PostgreSQL` 容器上完成真实库启动与登录 / `me` 接口验证
10. 已完成用户模块后端实现，覆盖分页查询、详情、新增、编辑、启用/停用、重置密码接口
11. 已完成用户管理页第一批开发，并通过前端构建验证
12. 已在真实 `PostgreSQL` 上验证用户分页、详情、新增、编辑、启用 / 停用、重置密码接口
13. 已完成角色模块后端实现，并通过真实 `PostgreSQL` 验证分页、详情、新增、编辑、启用 / 停用接口
14. 已完成角色管理页第一批开发，并通过前端构建验证
15. 已完成用户绑定角色接口和用户详情绑定角色区接入
16. 已在真实 `PostgreSQL` 上验证用户角色查询、覆盖更新、清空与恢复
17. 已完成角色绑定 Agent 接口和角色详情绑定 Agent 区接入
18. 已在真实 `PostgreSQL` 上验证 Agent 分页、详情，以及角色 Agent 查询、覆盖更新、清空与恢复
19. 已完成 Agent 管理页第一批开发，并通过前端构建验证
20. 已在真实 `PostgreSQL` 上验证 Agent 新增、详情、编辑、启用与停用接口
21. 已完成 Agent Tool 范围维护页面接入，并通过前端构建验证
22. 已在真实 `PostgreSQL` 上验证 Agent Tool 范围查询、覆盖更新与继承默认模式

当前未完成：

1. 无

---

## 9. 计划状态

本并行计划已完成并归档。`agent-core` 会话、上下文、Planner、Workflow、Tool/MCP、治理和 E2E 主链均已在后续阶段完成，后续状态统一以 `docs/MVP开发计划.md` 和 `docs/平台完成度与后续路线.md` 为准。

---

## 10. 本轮收口结论

1. `registry-center` 当前 P0 范围已经完成
2. 本文档不再承载当前待办，避免历史阶段状态误导开发
3. 后续若继续修改注册中心页面，按缺陷修复或生产增强处理
