-- 保留基础数据：
-- 1. 字典 dict_types / dict_items
-- 2. 模型 models
-- 3. 管理员用户 zc
-- 4. 管理员角色 ADMIN
-- 5. zc -> ADMIN 的用户角色关系
--
-- 删除其余测试/演示配置：
-- agents / prompts / tools / workflows 及其关联关系
-- 非基础用户、角色及多余 user_role_rel

BEGIN;

DELETE FROM role_agent_rel;
DELETE FROM agent_tool_rel;
DELETE FROM agent_workflow_rel;
DELETE FROM tool_bindings;
DELETE FROM tool_provider_instances;
DELETE FROM tool_providers;

DELETE FROM agents;
DELETE FROM prompts;
DELETE FROM tools;
DELETE FROM workflows;

DELETE FROM user_role_rel
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    JOIN roles r ON r.role_code = 'ADMIN'
    WHERE u.username = 'zc'
      AND u.id = user_role_rel.user_id
      AND r.id = user_role_rel.role_id
);

DELETE FROM roles
WHERE role_code <> 'ADMIN';

DELETE FROM users
WHERE username <> 'zc';

COMMIT;
