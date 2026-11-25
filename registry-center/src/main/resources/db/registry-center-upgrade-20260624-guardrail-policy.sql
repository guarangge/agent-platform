CREATE TABLE IF NOT EXISTS guardrail_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    policy_version INTEGER NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    description VARCHAR(500),
    rollout_mode VARCHAR(32) NOT NULL DEFAULT 'all',
    rollout_percentage INTEGER,
    rollout_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_role_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_agent_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_tool_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_workflow_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    scope_envs JSONB NOT NULL DEFAULT '[]'::jsonb,
    match_mode VARCHAR(32) NOT NULL DEFAULT 'always',
    require_non_empty_input BOOLEAN NOT NULL DEFAULT FALSE,
    max_input_length INTEGER,
    blocked_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    action_type VARCHAR(32) NOT NULL DEFAULT 'block',
    approval_node_name VARCHAR(128),
    approval_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    approval_role_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_guardrail_policies_policy_code ON guardrail_policies (policy_code);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_status ON guardrail_policies (status);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_action_type ON guardrail_policies (action_type);
CREATE INDEX IF NOT EXISTS idx_guardrail_policies_priority ON guardrail_policies (priority);

COMMENT ON TABLE guardrail_policies IS 'Guardrail 策略中心表';
COMMENT ON COLUMN guardrail_policies.id IS '主键ID';
COMMENT ON COLUMN guardrail_policies.policy_code IS '策略编码';
COMMENT ON COLUMN guardrail_policies.policy_name IS '策略名称';
COMMENT ON COLUMN guardrail_policies.policy_version IS '策略版本号';
COMMENT ON COLUMN guardrail_policies.priority IS '优先级，值越大优先级越高';
COMMENT ON COLUMN guardrail_policies.status IS '状态 draft/enabled/disabled';
COMMENT ON COLUMN guardrail_policies.description IS '策略描述';
COMMENT ON COLUMN guardrail_policies.rollout_mode IS '灰度模式 all/percentage/user_whitelist';
COMMENT ON COLUMN guardrail_policies.rollout_percentage IS '灰度百分比';
COMMENT ON COLUMN guardrail_policies.rollout_user_ids IS '灰度白名单用户ID列表';
COMMENT ON COLUMN guardrail_policies.scope_user_ids IS '作用域用户ID列表';
COMMENT ON COLUMN guardrail_policies.scope_role_codes IS '作用域角色编码列表';
COMMENT ON COLUMN guardrail_policies.scope_agent_ids IS '作用域Agent ID列表';
COMMENT ON COLUMN guardrail_policies.scope_tool_ids IS '作用域Tool ID列表';
COMMENT ON COLUMN guardrail_policies.scope_workflow_ids IS '作用域Workflow ID列表';
COMMENT ON COLUMN guardrail_policies.scope_envs IS '作用域环境列表';
COMMENT ON COLUMN guardrail_policies.match_mode IS '匹配模式 always/keyword_match';
COMMENT ON COLUMN guardrail_policies.require_non_empty_input IS '是否要求输入非空';
COMMENT ON COLUMN guardrail_policies.max_input_length IS '最大输入长度';
COMMENT ON COLUMN guardrail_policies.blocked_keywords IS '阻断关键词列表';
COMMENT ON COLUMN guardrail_policies.keywords IS '审批/匹配关键词列表';
COMMENT ON COLUMN guardrail_policies.action_type IS '动作类型 block/approval';
COMMENT ON COLUMN guardrail_policies.approval_node_name IS '审批节点名称';
COMMENT ON COLUMN guardrail_policies.approval_user_ids IS '审批指定用户ID列表';
COMMENT ON COLUMN guardrail_policies.approval_role_codes IS '审批指定角色编码列表';
COMMENT ON COLUMN guardrail_policies.reason IS '命中原因说明';
COMMENT ON COLUMN guardrail_policies.metadata IS '扩展元数据';
COMMENT ON COLUMN guardrail_policies.remark IS '备注';
COMMENT ON COLUMN guardrail_policies.created_at IS '创建时间';
COMMENT ON COLUMN guardrail_policies.created_by IS '创建人';
COMMENT ON COLUMN guardrail_policies.updated_at IS '更新时间';
COMMENT ON COLUMN guardrail_policies.updated_by IS '更新人';
COMMENT ON COLUMN guardrail_policies.is_deleted IS '逻辑删除标记';
