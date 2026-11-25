# registry-center 接口清单

## 1. 结论

当前接口清单只覆盖 `registry-center`，不混入 `agent-core`。

整理原则：

1. 先覆盖 MVP 稳定范围
2. 先做 P0，再补 P1
3. 接口路径按资源语义拆分
4. 接口返回结构统一
5. 与数据库设计保持一致，后续如有表设计变更，必须同步更新本文件和 `docs/数据库设计.md`

当前建议优先级：

1. P0：认证、当前用户、用户、角色、用户角色、角色 Agent、Agent、Tool、资源字典
2. P1：Workflow、Prompt

---

## 2. 通用约定

### 2.1 服务前缀

建议统一前缀：

`/api/registry`

### 2.2 返回结构

统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页返回建议：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

### 2.3 认证约定

1. 登录成功后由 `registry-center` 签发 `JWT Token`
2. 受保护接口统一通过 `Authorization: Bearer <token>` 访问
3. `agent-core` 后续使用同一套 Token 校验规则

### 2.4 状态字段约定

当前接口涉及的主要状态值：

1. User：`enabled`、`disabled`
2. Role：`enabled`、`disabled`
3. Agent：`draft`、`enabled`、`disabled`
4. Tool：`draft`、`enabled`、`disabled`
5. UserRoleRel：`enabled`
6. RoleAgentRel：`enabled`

---

## 3. P0 接口清单

## 3.0 资源字典模块

### 3.0.1 分页查询字典类型

- 方法：`GET`
- 路径：`/api/registry/dict-types`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `typeCode`
4. `typeName`
5. `status`

### 3.0.2 查询字典类型详情

- 方法：`GET`
- 路径：`/api/registry/dict-types/{dictTypeId}`

### 3.0.3 新增字典类型

- 方法：`POST`
- 路径：`/api/registry/dict-types`

### 3.0.4 编辑字典类型

- 方法：`PUT`
- 路径：`/api/registry/dict-types/{dictTypeId}`

### 3.0.5 启用字典类型

- 方法：`PUT`
- 路径：`/api/registry/dict-types/{dictTypeId}/enable`

### 3.0.6 停用字典类型

- 方法：`PUT`
- 路径：`/api/registry/dict-types/{dictTypeId}/disable`

### 3.0.7 分页查询字典项

- 方法：`GET`
- 路径：`/api/registry/dict-types/{dictTypeId}/items`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `itemCode`
4. `itemName`
5. `status`

### 3.0.8 新增字典项

- 方法：`POST`
- 路径：`/api/registry/dict-types/{dictTypeId}/items`

### 3.0.9 查询字典项详情

- 方法：`GET`
- 路径：`/api/registry/dict-items/{dictItemId}`

### 3.0.10 编辑字典项

- 方法：`PUT`
- 路径：`/api/registry/dict-items/{dictItemId}`

### 3.0.11 启用字典项

- 方法：`PUT`
- 路径：`/api/registry/dict-items/{dictItemId}/enable`

### 3.0.12 停用字典项

- 方法：`PUT`
- 路径：`/api/registry/dict-items/{dictItemId}/disable`

### 3.0.13 查询字典下拉选项

- 方法：`GET`
- 路径：`/api/registry/dicts/options`

查询参数建议：

1. `typeCodes`

示例：

```text
/api/registry/dicts/options?typeCodes=tool_type,tool_category,permission_level
```

## 3.1 认证模块

### 3.1.1 登录

- 方法：`POST`
- 路径：`/api/registry/auth/login`

请求体建议：

```json
{
  "username": "zc",
  "password": "<YOUR_PASSWORD>",
  "rememberMe": true
}
```

返回数据建议：

```json
{
  "accessToken": "jwt-token",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "refreshToken": "optional-refresh-token"
}
```

说明：

1. 当前阶段可先只发 `accessToken`
2. 是否启用 `refreshToken` 可在认证实现阶段再定

### 3.1.2 退出登录

- 方法：`POST`
- 路径：`/api/registry/auth/logout`

说明：

1. MVP 可先做前端清理登录态
2. 若后续做黑名单或 Token 撤销，再补服务端处理

### 3.1.3 获取当前登录用户

- 方法：`GET`
- 路径：`/api/registry/auth/me`

返回数据建议：

```json
{
  "userId": 1,
  "username": "zc",
  "displayName": "张晨",
  "status": "enabled",
  "roles": [
    {
      "roleId": 1,
      "roleCode": "ADMIN",
      "roleName": "平台管理员"
    }
  ],
  "menuScopes": [
    "dashboard",
    "agent",
    "user",
    "role",
    "chat"
  ]
}
```

### 3.1.4 刷新 Token

- 方法：`POST`
- 路径：`/api/registry/auth/refresh`

说明：

1. 当前列入接口清单
2. 若第一版不启用，可先不实现

---

## 3.2 用户管理模块

### 3.2.1 分页查询用户

- 方法：`GET`
- 路径：`/api/registry/users`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `username`
4. `displayName`
5. `status`

### 3.2.2 查询用户详情

- 方法：`GET`
- 路径：`/api/registry/users/{userId}`

### 3.2.3 新增用户

- 方法：`POST`
- 路径：`/api/registry/users`

请求体建议：

```json
{
  "username": "operator",
  "password": "<YOUR_PASSWORD>",
  "displayName": "运营同学",
  "email": "operator@example.com",
  "mobile": "13800000000",
  "status": "enabled",
  "remark": "运营角色测试用户"
}
```

### 3.2.4 编辑用户

- 方法：`PUT`
- 路径：`/api/registry/users/{userId}`

### 3.2.5 启用用户

- 方法：`PUT`
- 路径：`/api/registry/users/{userId}/enable`

### 3.2.6 停用用户

- 方法：`PUT`
- 路径：`/api/registry/users/{userId}/disable`

### 3.2.7 重置密码

- 方法：`PUT`
- 路径：`/api/registry/users/{userId}/reset-password`

请求体建议：

```json
{
  "newPassword": "<YOUR_NEW_PASSWORD>"
}
```

---

## 3.3 角色管理模块

### 3.3.1 分页查询角色

- 方法：`GET`
- 路径：`/api/registry/roles`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `roleCode`
4. `roleName`
5. `status`

### 3.3.2 查询角色详情

- 方法：`GET`
- 路径：`/api/registry/roles/{roleId}`

### 3.3.3 新增角色

- 方法：`POST`
- 路径：`/api/registry/roles`

请求体建议：

```json
{
  "roleCode": "OPERATOR",
  "roleName": "运营角色",
  "status": "enabled",
  "menuScope": [
    "dashboard",
    "agent",
    "workflow",
    "prompt",
    "chat"
  ],
  "description": "运营侧使用角色"
}
```

### 3.3.4 编辑角色

- 方法：`PUT`
- 路径：`/api/registry/roles/{roleId}`

### 3.3.5 启用角色

- 方法：`PUT`
- 路径：`/api/registry/roles/{roleId}/enable`

### 3.3.6 停用角色

- 方法：`PUT`
- 路径：`/api/registry/roles/{roleId}/disable`

---

## 3.4 用户绑定角色模块

### 3.4.1 查询用户已绑定角色

- 方法：`GET`
- 路径：`/api/registry/users/{userId}/roles`

### 3.4.2 绑定角色

- 方法：`POST`
- 路径：`/api/registry/users/{userId}/roles`

请求体建议：

```json
{
  "roleIds": [1, 2]
}
```

说明：

1. 建议采用“增量绑定”语义
2. 对已绑定角色重复提交时应幂等处理

### 3.4.3 解绑角色

- 方法：`DELETE`
- 路径：`/api/registry/users/{userId}/roles/{roleId}`

### 3.4.4 覆盖式更新用户角色

- 方法：`PUT`
- 路径：`/api/registry/users/{userId}/roles`

请求体建议：

```json
{
  "roleIds": [1, 3]
}
```

说明：

1. 如果使用覆盖式更新，这个接口可以替代“绑定 + 解绑”组合
2. 第一版建议二选一，不要两套语义都重实现

---

## 3.5 角色绑定 Agent 模块

### 3.5.1 查询角色已绑定 Agent

- 方法：`GET`
- 路径：`/api/registry/roles/{roleId}/agents`

当前实现返回字段：

1. `agentId`
2. `agentCode`
3. `agentName`
4. `status`
5. `version`
6. `description`
7. `boundAt`

### 3.5.2 绑定 Agent

- 方法：`POST`
- 路径：`/api/registry/roles/{roleId}/agents`

请求体建议：

```json
{
  "agentIds": [1, 2]
}
```

### 3.5.3 解绑 Agent

- 方法：`DELETE`
- 路径：`/api/registry/roles/{roleId}/agents/{agentId}`

### 3.5.4 覆盖式更新角色 Agent

- 方法：`PUT`
- 路径：`/api/registry/roles/{roleId}/agents`

请求体建议：

```json
{
  "agentIds": [1, 3]
}
```

说明：

1. 同样建议第一版在“增量模式”和“覆盖模式”里选一种主实现
2. 当前后端已实现 `GET /api/registry/roles/{roleId}/agents`
3. 当前后端已实现 `PUT /api/registry/roles/{roleId}/agents`
4. 当前版本未单独实现 `POST` 绑定和 `DELETE` 解绑，统一通过覆盖式更新处理

---

## 3.6 Agent 管理模块

### 3.6.1 分页查询 Agent

- 方法：`GET`
- 路径：`/api/registry/agents`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `agentCode`
4. `agentName`
5. `status`
6. `version`

当前实现返回字段：

1. `agentId`
2. `agentCode`
3. `agentName`
4. `description`
5. `version`
6. `defaultModel`
7. `status`
8. `createdAt`

### 3.6.2 查询 Agent 详情

- 方法：`GET`
- 路径：`/api/registry/agents/{agentId}`

当前实现返回字段：

1. `agentId`
2. `agentCode`
3. `agentName`
4. `description`
5. `version`
6. `defaultModel`
7. `defaultPromptId`
8. `toolScopeMode`
9. `status`
10. `remark`
11. `createdAt`
12. `updatedAt`
13. `createdBy`
14. `updatedBy`

### 3.6.3 新增 Agent

- 方法：`POST`
- 路径：`/api/registry/agents`

请求体建议：

```json
{
  "agentCode": "gaokao_assistant",
  "agentName": "高考填报 Agent",
  "description": "用于高考志愿分析的 Agent",
  "version": "v1.0",
  "defaultModel": "gpt-4.1",
  "defaultPromptId": 1,
  "toolScopeMode": "inherit_and_append",
  "status": "draft",
  "remark": "MVP 演示 Agent"
}
```

### 3.6.4 编辑 Agent

- 方法：`PUT`
- 路径：`/api/registry/agents/{agentId}`

### 3.6.5 启用 Agent

- 方法：`PUT`
- 路径：`/api/registry/agents/{agentId}/enable`

### 3.6.6 停用 Agent

- 方法：`PUT`
- 路径：`/api/registry/agents/{agentId}/disable`

### 3.6.7 查询 Agent 可用 Tool

- 方法：`GET`
- 路径：`/api/registry/agents/{agentId}/tools`

当前实现返回字段：

1. `agentId`
2. `toolScopeMode`
3. `selectedToolIds`
4. `tools`
5. `availableTools`

### 3.6.8 更新 Agent Tool 范围

- 方法：`PUT`
- 路径：`/api/registry/agents/{agentId}/tools`

请求体建议：

说明：

1. 当前后端最小可用能力已实现 `GET /api/registry/agents`
2. 当前后端最小可用能力已实现 `GET /api/registry/agents/{agentId}`
3. 当前后端已实现 `POST /api/registry/agents`
4. 当前后端已实现 `PUT /api/registry/agents/{agentId}`
5. 当前后端已实现 `PUT /api/registry/agents/{agentId}/enable`
6. 当前后端已实现 `PUT /api/registry/agents/{agentId}/disable`
7. 当前后端已实现 `GET /api/registry/agents/{agentId}/tools`
8. 当前后端已实现 `PUT /api/registry/agents/{agentId}/tools`
9. 当前后端已实现 `GET /api/registry/agents/{agentId}/workflows`
10. 当前后端已实现 `PUT /api/registry/agents/{agentId}/workflows`

```json
{
  "toolScopeMode": "inherit_and_append",
  "toolIds": [1, 2, 3]
}
```

说明：

1. `toolScopeMode` 当前支持 `inherit_default`、`inherit_and_append`、`custom`
2. 当 `toolScopeMode = inherit_default` 时，可忽略 `toolIds`
3. 当 `toolScopeMode = inherit_and_append` 时，`toolIds` 表示在默认 Tool 集基础上追加的行业 Tool
4. 当 `toolScopeMode = custom` 时，`toolIds` 表示 Agent 独占的自定义 Tool 集
5. 当前接口语义要和 `agent_tool_rel` 设计保持一致
6. 当前 `GET /api/registry/agents/{agentId}/tools` 会同时返回当前已选 Tool 和可选 Tool 列表，便于前端直接维护

### 3.6.9 查询 Agent 可用 Workflow

- 方法：`GET`
- 路径：`/api/registry/agents/{agentId}/workflows`

当前实现返回字段：

1. `agentId`
2. `selectedWorkflowIds`
3. `workflows`
4. `availableWorkflows`

### 3.6.10 更新 Agent Workflow 范围

- 方法：`PUT`
- 路径：`/api/registry/agents/{agentId}/workflows`

请求体建议：

```json
{
  "workflowIds": [1, 2]
}
```

说明：

1. Agent 只维护可用 Workflow 集合，不设置默认 Workflow
2. `workflowIds` 传空数组表示清空当前 Agent 的 Workflow 绑定
3. 当前 `GET /api/registry/agents/{agentId}/workflows` 会同时返回当前已选 Workflow 和可选 Workflow 列表，便于前端直接维护

---

## 4. P1 接口清单

## 4.1 Tool 管理模块

### 4.1.1 分页查询 Tool

- 方法：`GET`
- 路径：`/api/registry/tools`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `toolCode`
4. `toolName`
5. `toolType`
6. `toolCategory`
7. `status`

### 4.1.2 查询 Tool 详情

- 方法：`GET`
- 路径：`/api/registry/tools/{toolId}`

### 4.1.3 新增 Tool

- 方法：`POST`
- 路径：`/api/registry/tools`

### 4.1.4 编辑 Tool

- 方法：`PUT`
- 路径：`/api/registry/tools/{toolId}`

### 4.1.5 启用 Tool

- 方法：`PUT`
- 路径：`/api/registry/tools/{toolId}/enable`

### 4.1.6 停用 Tool

- 方法：`PUT`
- 路径：`/api/registry/tools/{toolId}/disable`

当前实现说明：

1. 当前后端已实现 Tool 分页、详情、新增、编辑、启用、停用
2. 当前前端已完成 Tool 管理页第一批，覆盖列表、详情、新增、编辑、启停
3. `inputSchema`、`outputSchema`、`metadata` 当前按 JSON object 传输和存储
4. Tool 当前状态值为 `draft`、`enabled`、`disabled`
5. Tool 当前已支持 `toolType + toolCategory` 双维度分类

---

## 4.2 Workflow 管理模块

### 4.2.1 分页查询 Workflow

- 方法：`GET`
- 路径：`/api/registry/workflows`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `workflowCode`
4. `workflowName`
5. `workflowType`
6. `workflowCategory`
7. `version`
8. `status`

### 4.2.2 查询 Workflow 详情

- 方法：`GET`
- 路径：`/api/registry/workflows/{workflowId}`

### 4.2.3 新增 Workflow

- 方法：`POST`
- 路径：`/api/registry/workflows`

### 4.2.4 编辑 Workflow

- 方法：`PUT`
- 路径：`/api/registry/workflows/{workflowId}`

### 4.2.5 发布 Workflow

- 方法：`PUT`
- 路径：`/api/registry/workflows/{workflowId}/publish`

### 4.2.6 停用 Workflow

- 方法：`PUT`
- 路径：`/api/registry/workflows/{workflowId}/disable`

当前实现说明：

1. 当前后端已实现 Workflow 分页、详情、新增、编辑、发布、停用
2. 当前前端已完成 Workflow 管理页第一批，覆盖列表、详情、新增、编辑、发布、停用
3. `definitionJson`、`metadata` 当前按 JSON object 传输和存储
4. Workflow 当前状态值为 `draft`、`published`、`disabled`
5. Workflow 当前已支持 `workflowType + workflowCategory` 双维度分类

---

## 4.3 Prompt 管理模块

### 4.3.1 分页查询 Prompt

- 方法：`GET`
- 路径：`/api/registry/prompts`

查询参数建议：

1. `pageNum`
2. `pageSize`
3. `promptCode`
4. `promptName`
5. `promptType`
6. `promptCategory`
7. `status`

### 4.3.2 查询 Prompt 详情

- 方法：`GET`
- 路径：`/api/registry/prompts/{promptId}`

### 4.3.3 新增 Prompt

- 方法：`POST`
- 路径：`/api/registry/prompts`

### 4.3.4 编辑 Prompt

- 方法：`PUT`
- 路径：`/api/registry/prompts/{promptId}`

### 4.3.5 启用 Prompt

- 方法：`PUT`
- 路径：`/api/registry/prompts/{promptId}/enable`

### 4.3.6 停用 Prompt

- 方法：`PUT`
- 路径：`/api/registry/prompts/{promptId}/disable`

当前实现说明：

1. 当前后端已实现 Prompt 分页、详情、新增、编辑、启用、停用
2. 当前前端已完成 Prompt 管理页第一批，覆盖列表、详情、新增、编辑、启停
3. `metadata` 当前按 JSON object 传输和存储，`content` 按文本字段处理
4. Prompt 当前状态值为 `draft`、`enabled`、`disabled`
5. Prompt 当前已支持 `promptType + promptCategory` 双维度分类

---

## 5. 当前不放入 P0 的接口

当前先不放入第一批实现：

1. Knowledge Base 管理接口
2. Sandbox 配置接口
3. 细粒度资源权限接口
4. Workflow 节点级管理接口
5. 资源发布历史接口

原因：

1. 当前 MVP 还不依赖
2. 这些接口依赖后续模型进一步稳定

---

## 6. 实现顺序建议

建议按下面顺序实现：

1. `auth`
2. `users`
3. `roles`
4. `users/{userId}/roles`
5. `roles/{roleId}/agents`
6. `agents`
7. `agents/{agentId}/tools`
8. `agents/{agentId}/workflows`

P1 再做：

1. `tools`
2. `workflows`
3. `prompts`

---

## 7. 与数据库设计的映射关系

当前主要映射关系如下：

1. 用户接口对应 `users`
2. 角色接口对应 `roles`
3. 用户角色接口对应 `user_role_rel`
4. Agent 接口对应 `agents`
5. Agent Tool 范围接口对应 `agent_tool_rel`
6. Agent Workflow 范围接口对应 `agent_workflow_rel`
7. 角色 Agent 接口对应 `role_agent_rel`
8. Tool 接口对应 `tools`
9. Workflow 接口对应 `workflows`
10. Prompt 接口对应 `prompts`

---

## 8. 下一步建议

接口清单完成后，建议继续：

1. 输出 `registry-center` 的 Controller / Service 模块划分
2. 开始认证模块实现
3. 再实现用户、角色、用户角色、角色 Agent
