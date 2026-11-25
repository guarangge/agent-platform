CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    conversation_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    default_model VARCHAR(64),
    default_prompt_id BIGINT,
    default_prompt_name VARCHAR(128),
    decision_prompt_id BIGINT,
    decision_prompt_name VARCHAR(128),
    planner_prompt_id BIGINT,
    planner_prompt_name VARCHAR(128),
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    last_message_at TIMESTAMP,
    last_message_preview VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversations_code ON conversations (conversation_code);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations (user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_agent_id ON conversations (agent_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at ON conversations (last_message_at);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON conversations (status);

ALTER TABLE conversations DROP COLUMN IF EXISTS workflow_id;
ALTER TABLE conversations DROP COLUMN IF EXISTS workflow_name;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS decision_prompt_id BIGINT;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS decision_prompt_name VARCHAR(128);
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS planner_prompt_id BIGINT;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS planner_prompt_name VARCHAR(128);
COMMENT ON COLUMN conversations.planner_prompt_id IS '规划Prompt ID';
COMMENT ON COLUMN conversations.planner_prompt_name IS '规划Prompt名称';

CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    sequence_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    response_status VARCHAR(32) NOT NULL DEFAULT 'success',
    error_message VARCHAR(1000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages (conversation_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_conversation_sequence_deleted ON messages (conversation_id, sequence_no, is_deleted);
CREATE INDEX IF NOT EXISTS idx_messages_message_type ON messages (message_type);

CREATE TABLE IF NOT EXISTS conversation_memories (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    memory_summary TEXT,
    preference_summary VARCHAR(500),
    last_user_message VARCHAR(500),
    last_assistant_summary VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_memories_conversation_deleted
    ON conversation_memories (conversation_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_conversation_memories_updated_at ON conversation_memories (updated_at);

COMMENT ON TABLE conversation_memories IS '会话记忆表';
COMMENT ON COLUMN conversation_memories.id IS '主键ID';
COMMENT ON COLUMN conversation_memories.conversation_id IS '会话ID';
COMMENT ON COLUMN conversation_memories.memory_summary IS '会话记忆摘要';
COMMENT ON COLUMN conversation_memories.preference_summary IS '会话偏好摘要';
COMMENT ON COLUMN conversation_memories.last_user_message IS '最近用户消息摘要';
COMMENT ON COLUMN conversation_memories.last_assistant_summary IS '最近助手响应摘要';
COMMENT ON COLUMN conversation_memories.metadata IS '扩展信息';
COMMENT ON COLUMN conversation_memories.created_at IS '创建时间';
COMMENT ON COLUMN conversation_memories.created_by IS '创建人';
COMMENT ON COLUMN conversation_memories.updated_at IS '更新时间';
COMMENT ON COLUMN conversation_memories.updated_by IS '更新人';
COMMENT ON COLUMN conversation_memories.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS user_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    preference_summary VARCHAR(500),
    attention_summary VARCHAR(500),
    fact_summary VARCHAR(500),
    goal_summary VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS agent_id BIGINT;
ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS fact_summary VARCHAR(500);
ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS goal_summary VARCHAR(500);

DROP INDEX IF EXISTS uk_user_memories_user_deleted;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memories_user_agent_deleted
    ON user_memories (user_id, agent_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_user_memories_updated_at ON user_memories (updated_at);

COMMENT ON TABLE user_memories IS '用户记忆表';
COMMENT ON COLUMN user_memories.id IS '主键ID';
COMMENT ON COLUMN user_memories.user_id IS '用户ID';
COMMENT ON COLUMN user_memories.agent_id IS 'Agent ID，同一用户按Agent维度拆分记忆';
COMMENT ON COLUMN user_memories.preference_summary IS '用户偏好摘要';
COMMENT ON COLUMN user_memories.attention_summary IS '用户关注点摘要';
COMMENT ON COLUMN user_memories.fact_summary IS '用户长期事实摘要';
COMMENT ON COLUMN user_memories.goal_summary IS '用户长期目标摘要';
COMMENT ON COLUMN user_memories.metadata IS '扩展信息';
COMMENT ON COLUMN user_memories.created_at IS '创建时间';
COMMENT ON COLUMN user_memories.created_by IS '创建人';
COMMENT ON COLUMN user_memories.updated_at IS '更新时间';
COMMENT ON COLUMN user_memories.updated_by IS '更新人';
COMMENT ON COLUMN user_memories.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS task_memory_snapshots (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    memory_summary TEXT,
    key_findings TEXT,
    final_response_summary VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_memory_snapshots_task_deleted
    ON task_memory_snapshots (task_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_task_memory_snapshots_conversation_id ON task_memory_snapshots (conversation_id);
CREATE INDEX IF NOT EXISTS idx_task_memory_snapshots_updated_at ON task_memory_snapshots (updated_at);

COMMENT ON TABLE task_memory_snapshots IS '任务记忆快照表';
COMMENT ON COLUMN task_memory_snapshots.id IS '主键ID';
COMMENT ON COLUMN task_memory_snapshots.task_id IS '任务ID';
COMMENT ON COLUMN task_memory_snapshots.conversation_id IS '会话ID';
COMMENT ON COLUMN task_memory_snapshots.memory_summary IS '任务记忆摘要';
COMMENT ON COLUMN task_memory_snapshots.key_findings IS '关键发现摘要';
COMMENT ON COLUMN task_memory_snapshots.final_response_summary IS '最终响应摘要';
COMMENT ON COLUMN task_memory_snapshots.metadata IS '扩展信息';
COMMENT ON COLUMN task_memory_snapshots.created_at IS '创建时间';
COMMENT ON COLUMN task_memory_snapshots.created_by IS '创建人';
COMMENT ON COLUMN task_memory_snapshots.updated_at IS '更新时间';
COMMENT ON COLUMN task_memory_snapshots.updated_by IS '更新人';
COMMENT ON COLUMN task_memory_snapshots.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    task_code VARCHAR(64) NOT NULL,
    run_code VARCHAR(64),
    trace_id VARCHAR(64),
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    parent_task_id BIGINT,
    root_task_id BIGINT,
    trigger_task_step_id BIGINT,
    task_kind VARCHAR(32) NOT NULL DEFAULT 'primary',
    execution_type VARCHAR(32),
    current_plan_version INTEGER,
    execution_chain_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    prompt_id BIGINT,
    prompt_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message VARCHAR(1000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE tasks DROP COLUMN IF EXISTS workflow_id;
ALTER TABLE tasks DROP COLUMN IF EXISTS workflow_name;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS run_code VARCHAR(64);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS root_task_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_task_step_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS task_kind VARCHAR(32) NOT NULL DEFAULT 'primary';
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS execution_type VARCHAR(32);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS current_plan_version INTEGER;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS execution_chain_json JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tasks_code ON tasks (task_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tasks_run_code ON tasks (run_code) WHERE run_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tasks_conversation_id ON tasks (conversation_id);
CREATE INDEX IF NOT EXISTS idx_tasks_message_id ON tasks (message_id);
CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks (parent_task_id);
CREATE INDEX IF NOT EXISTS idx_tasks_root_task_id ON tasks (root_task_id);
CREATE INDEX IF NOT EXISTS idx_tasks_trigger_task_step_id ON tasks (trigger_task_step_id);
CREATE INDEX IF NOT EXISTS idx_tasks_task_kind ON tasks (task_kind);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks (status);
CREATE INDEX IF NOT EXISTS idx_tasks_started_at ON tasks (started_at);

COMMENT ON TABLE tasks IS '执行任务表';
COMMENT ON COLUMN tasks.id IS '主键ID';
COMMENT ON COLUMN tasks.task_code IS '任务编码，唯一';
COMMENT ON COLUMN tasks.run_code IS '关联异步运行编码，用于幂等执行和队列回链';
COMMENT ON COLUMN tasks.trace_id IS '本次任务执行追踪ID';
COMMENT ON COLUMN tasks.conversation_id IS '所属会话ID';
COMMENT ON COLUMN tasks.message_id IS '触发任务的用户消息ID';
COMMENT ON COLUMN tasks.agent_id IS '执行任务的Agent ID';
COMMENT ON COLUMN tasks.parent_task_id IS '父任务ID';
COMMENT ON COLUMN tasks.root_task_id IS '根任务ID';
COMMENT ON COLUMN tasks.trigger_task_step_id IS '触发当前任务的父步骤ID';
COMMENT ON COLUMN tasks.task_kind IS '任务类型，如 primary、delegated';
COMMENT ON COLUMN tasks.execution_type IS '本次任务的执行分类，取值如 direct_reply、tool_only、workflow_only、hybrid';
COMMENT ON COLUMN tasks.current_plan_version IS '当前生效的计划版本号';
COMMENT ON COLUMN tasks.execution_chain_json IS '本次任务的调用链路快照，按顺序记录命中的 Workflow、Tool 等执行资源';
COMMENT ON COLUMN tasks.prompt_id IS 'Prompt ID快照';
COMMENT ON COLUMN tasks.prompt_name IS 'Prompt名称快照';
COMMENT ON COLUMN tasks.status IS '任务状态';
COMMENT ON COLUMN tasks.started_at IS '任务开始时间';
COMMENT ON COLUMN tasks.finished_at IS '任务结束时间';
COMMENT ON COLUMN tasks.error_message IS '失败原因';
COMMENT ON COLUMN tasks.metadata IS '扩展信息';
COMMENT ON COLUMN tasks.created_at IS '创建时间';
COMMENT ON COLUMN tasks.created_by IS '创建人';
COMMENT ON COLUMN tasks.updated_at IS '更新时间';
COMMENT ON COLUMN tasks.updated_by IS '更新人';
COMMENT ON COLUMN tasks.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGSERIAL PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    input_text TEXT NOT NULL,
    user_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 2,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP,
    heartbeat_at TIMESTAMP,
    worker_id VARCHAR(160),
    task_id BIGINT,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_runs_code ON agent_runs (run_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_runs_idempotency
    ON agent_runs (conversation_id, user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_runs_claim
    ON agent_runs (status, available_at, lease_until)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_runs_conversation ON agent_runs (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_runs_task_id ON agent_runs (task_id);

COMMENT ON TABLE agent_runs IS 'Agent 异步运行持久队列表';
COMMENT ON COLUMN agent_runs.id IS '主键ID';
COMMENT ON COLUMN agent_runs.run_code IS '运行编码，唯一';
COMMENT ON COLUMN agent_runs.conversation_id IS '所属会话ID';
COMMENT ON COLUMN agent_runs.user_id IS '发起用户ID';
COMMENT ON COLUMN agent_runs.input_text IS '用户输入文本';
COMMENT ON COLUMN agent_runs.user_context IS '入队时固化的用户身份与权限上下文';
COMMENT ON COLUMN agent_runs.idempotency_key IS '客户端幂等键';
COMMENT ON COLUMN agent_runs.status IS '运行状态：queued、running、completed、waiting_approval、failed、cancelled';
COMMENT ON COLUMN agent_runs.attempt_count IS '已领取执行次数';
COMMENT ON COLUMN agent_runs.max_attempts IS '最大领取执行次数';
COMMENT ON COLUMN agent_runs.available_at IS '下次可领取时间';
COMMENT ON COLUMN agent_runs.lease_until IS 'Worker 租约截止时间';
COMMENT ON COLUMN agent_runs.heartbeat_at IS 'Worker 最近心跳时间';
COMMENT ON COLUMN agent_runs.worker_id IS '当前 Worker 实例标识';
COMMENT ON COLUMN agent_runs.task_id IS '关联执行任务ID';
COMMENT ON COLUMN agent_runs.cancel_requested IS '是否已请求取消';
COMMENT ON COLUMN agent_runs.error_message IS '失败原因';
COMMENT ON COLUMN agent_runs.started_at IS '首次开始时间';
COMMENT ON COLUMN agent_runs.finished_at IS '结束时间';
COMMENT ON COLUMN agent_runs.created_at IS '创建时间';
COMMENT ON COLUMN agent_runs.updated_at IS '更新时间';
COMMENT ON COLUMN agent_runs.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS task_delegations (
    id BIGSERIAL PRIMARY KEY,
    parent_task_id BIGINT NOT NULL,
    parent_task_step_id BIGINT NOT NULL,
    child_task_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    delegate_agent_id BIGINT NOT NULL,
    delegate_agent_code VARCHAR(64) NOT NULL,
    delegate_agent_name VARCHAR(128) NOT NULL,
    delegate_instruction TEXT,
    trace_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'processing',
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_task_delegations_parent_task_id ON task_delegations (parent_task_id);
CREATE INDEX IF NOT EXISTS idx_task_delegations_parent_task_step_id ON task_delegations (parent_task_step_id);
CREATE INDEX IF NOT EXISTS idx_task_delegations_child_task_id ON task_delegations (child_task_id);
CREATE INDEX IF NOT EXISTS idx_task_delegations_conversation_id ON task_delegations (conversation_id);
CREATE INDEX IF NOT EXISTS idx_task_delegations_trace_id ON task_delegations (trace_id);
CREATE INDEX IF NOT EXISTS idx_task_delegations_status ON task_delegations (status);

COMMENT ON TABLE task_delegations IS '任务委派记录表';
COMMENT ON COLUMN task_delegations.id IS '主键ID';
COMMENT ON COLUMN task_delegations.parent_task_id IS '父任务ID';
COMMENT ON COLUMN task_delegations.parent_task_step_id IS '父任务步骤ID';
COMMENT ON COLUMN task_delegations.child_task_id IS '子任务ID';
COMMENT ON COLUMN task_delegations.conversation_id IS '会话ID';
COMMENT ON COLUMN task_delegations.delegate_agent_id IS '被委派Agent ID';
COMMENT ON COLUMN task_delegations.delegate_agent_code IS '被委派Agent编码';
COMMENT ON COLUMN task_delegations.delegate_agent_name IS '被委派Agent名称';
COMMENT ON COLUMN task_delegations.delegate_instruction IS '委派指令';
COMMENT ON COLUMN task_delegations.trace_id IS '子任务追踪ID';
COMMENT ON COLUMN task_delegations.status IS '委派状态';
COMMENT ON COLUMN task_delegations.input_snapshot IS '委派输入快照';
COMMENT ON COLUMN task_delegations.output_snapshot IS '委派输出快照';
COMMENT ON COLUMN task_delegations.error_message IS '错误信息';
COMMENT ON COLUMN task_delegations.created_at IS '创建时间';
COMMENT ON COLUMN task_delegations.created_by IS '创建人';
COMMENT ON COLUMN task_delegations.updated_at IS '更新时间';
COMMENT ON COLUMN task_delegations.updated_by IS '更新人';
COMMENT ON COLUMN task_delegations.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS guardrail_policy_hits (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_step_id BIGINT,
    agent_id BIGINT NOT NULL,
    trace_id VARCHAR(64),
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    policy_version INTEGER,
    decision_type VARCHAR(32) NOT NULL,
    resource_kind VARCHAR(32) NOT NULL,
    resource_id BIGINT,
    resource_code VARCHAR(64),
    resource_name VARCHAR(128),
    hit_detail VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_conversation_id ON guardrail_policy_hits (conversation_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_task_id ON guardrail_policy_hits (task_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_trace_id ON guardrail_policy_hits (trace_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_policy_code ON guardrail_policy_hits (policy_code);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_decision_type ON guardrail_policy_hits (decision_type);
CREATE INDEX IF NOT EXISTS idx_guardrail_policy_hits_created_at ON guardrail_policy_hits (created_at);

COMMENT ON TABLE guardrail_policy_hits IS 'Guardrail 策略命中记录表';
COMMENT ON COLUMN guardrail_policy_hits.id IS '主键ID';
COMMENT ON COLUMN guardrail_policy_hits.conversation_id IS '会话ID';
COMMENT ON COLUMN guardrail_policy_hits.task_id IS '任务ID';
COMMENT ON COLUMN guardrail_policy_hits.task_step_id IS '任务步骤ID';
COMMENT ON COLUMN guardrail_policy_hits.agent_id IS 'Agent ID';
COMMENT ON COLUMN guardrail_policy_hits.trace_id IS '追踪ID';
COMMENT ON COLUMN guardrail_policy_hits.policy_code IS '策略编码';
COMMENT ON COLUMN guardrail_policy_hits.policy_name IS '策略名称';
COMMENT ON COLUMN guardrail_policy_hits.policy_version IS '策略版本';
COMMENT ON COLUMN guardrail_policy_hits.decision_type IS '最终决策类型 block/approval';
COMMENT ON COLUMN guardrail_policy_hits.resource_kind IS '命中的资源类型';
COMMENT ON COLUMN guardrail_policy_hits.resource_id IS '命中的资源ID';
COMMENT ON COLUMN guardrail_policy_hits.resource_code IS '命中的资源编码';
COMMENT ON COLUMN guardrail_policy_hits.resource_name IS '命中的资源名称';
COMMENT ON COLUMN guardrail_policy_hits.hit_detail IS '命中说明';
COMMENT ON COLUMN guardrail_policy_hits.metadata IS '命中元数据';
COMMENT ON COLUMN guardrail_policy_hits.created_at IS '创建时间';
COMMENT ON COLUMN guardrail_policy_hits.created_by IS '创建人';
COMMENT ON COLUMN guardrail_policy_hits.updated_at IS '更新时间';
COMMENT ON COLUMN guardrail_policy_hits.updated_by IS '更新人';
COMMENT ON COLUMN guardrail_policy_hits.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS tool_health_checks (
    id BIGSERIAL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64),
    transport_type VARCHAR(64),
    provider_code VARCHAR(64),
    provider_name VARCHAR(128),
    provider_instance_code VARCHAR(64),
    provider_instance_name VARCHAR(128),
    environment_code VARCHAR(64),
    endpoint VARCHAR(512),
    operation_name VARCHAR(128),
    operation_type VARCHAR(64),
    health_status VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(1000),
    failure_reason VARCHAR(1000),
    status_code INTEGER,
    latency_ms BIGINT,
    output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tool_health_checks_tool_id ON tool_health_checks (tool_id);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_tool_checked_at ON tool_health_checks (tool_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_instance_code ON tool_health_checks (provider_instance_code);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_health_status ON tool_health_checks (health_status);
CREATE INDEX IF NOT EXISTS idx_tool_health_checks_checked_at ON tool_health_checks (checked_at);

COMMENT ON TABLE tool_health_checks IS 'Tool健康检查历史表';
COMMENT ON COLUMN tool_health_checks.id IS '主键ID';
COMMENT ON COLUMN tool_health_checks.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_health_checks.tool_code IS 'Tool编码';
COMMENT ON COLUMN tool_health_checks.tool_name IS 'Tool名称';
COMMENT ON COLUMN tool_health_checks.provider_type IS 'Provider类型';
COMMENT ON COLUMN tool_health_checks.transport_type IS '传输协议';
COMMENT ON COLUMN tool_health_checks.provider_code IS 'Provider编码';
COMMENT ON COLUMN tool_health_checks.provider_name IS 'Provider名称';
COMMENT ON COLUMN tool_health_checks.provider_instance_code IS 'Provider实例编码';
COMMENT ON COLUMN tool_health_checks.provider_instance_name IS 'Provider实例名称';
COMMENT ON COLUMN tool_health_checks.environment_code IS '环境编码';
COMMENT ON COLUMN tool_health_checks.endpoint IS '实例地址';
COMMENT ON COLUMN tool_health_checks.operation_name IS '绑定操作名';
COMMENT ON COLUMN tool_health_checks.operation_type IS '绑定操作类型';
COMMENT ON COLUMN tool_health_checks.health_status IS '健康状态 healthy/unhealthy/unknown';
COMMENT ON COLUMN tool_health_checks.success IS '是否成功';
COMMENT ON COLUMN tool_health_checks.message IS '检查结果说明';
COMMENT ON COLUMN tool_health_checks.failure_reason IS '最近失败原因';
COMMENT ON COLUMN tool_health_checks.status_code IS 'Provider返回状态码';
COMMENT ON COLUMN tool_health_checks.latency_ms IS '检查耗时毫秒';
COMMENT ON COLUMN tool_health_checks.output_snapshot IS '检查输出快照';
COMMENT ON COLUMN tool_health_checks.checked_at IS '检查时间';
COMMENT ON COLUMN tool_health_checks.created_at IS '创建时间';
COMMENT ON COLUMN tool_health_checks.created_by IS '创建人';
COMMENT ON COLUMN tool_health_checks.updated_at IS '更新时间';
COMMENT ON COLUMN tool_health_checks.updated_by IS '更新人';
COMMENT ON COLUMN tool_health_checks.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS task_steps (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    step_no INTEGER NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    retry_count INTEGER NOT NULL DEFAULT 0,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS task_traces (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    task_step_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    span_name VARCHAR(128) NOT NULL,
    trace_status VARCHAR(32) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(1000),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_task_traces_task_id ON task_traces (task_id);
CREATE INDEX IF NOT EXISTS idx_task_traces_trace_id ON task_traces (trace_id);
CREATE INDEX IF NOT EXISTS idx_task_traces_task_step_id ON task_traces (task_step_id);
CREATE INDEX IF NOT EXISTS idx_task_traces_event_type ON task_traces (event_type);
CREATE INDEX IF NOT EXISTS idx_task_traces_trace_status ON task_traces (trace_status);
CREATE INDEX IF NOT EXISTS idx_task_traces_started_at ON task_traces (started_at);
CREATE INDEX IF NOT EXISTS idx_task_traces_task_started_at ON task_traces (task_id, started_at);

ALTER TABLE task_steps
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_task_steps_task_id ON task_steps (task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_steps_task_step_deleted ON task_steps (task_id, step_no, is_deleted);
CREATE INDEX IF NOT EXISTS idx_task_steps_status ON task_steps (status);

COMMENT ON TABLE task_steps IS '任务步骤表';
COMMENT ON COLUMN task_steps.id IS '主键ID';
COMMENT ON COLUMN task_steps.task_id IS '任务ID';
COMMENT ON COLUMN task_steps.step_no IS '步骤序号';
COMMENT ON COLUMN task_steps.step_type IS '步骤类型';
COMMENT ON COLUMN task_steps.step_name IS '步骤名称';
COMMENT ON COLUMN task_steps.status IS '步骤状态';
COMMENT ON COLUMN task_steps.retry_count IS '当前步骤重试次数';
COMMENT ON COLUMN task_steps.input_snapshot IS '步骤输入快照';
COMMENT ON COLUMN task_steps.output_snapshot IS '步骤输出快照';
COMMENT ON COLUMN task_steps.error_message IS '失败原因';
COMMENT ON COLUMN task_steps.started_at IS '步骤开始时间';
COMMENT ON COLUMN task_steps.finished_at IS '步骤结束时间';
COMMENT ON COLUMN task_steps.created_at IS '创建时间';
COMMENT ON COLUMN task_steps.created_by IS '创建人';
COMMENT ON COLUMN task_steps.updated_at IS '更新时间';
COMMENT ON COLUMN task_steps.updated_by IS '更新人';
COMMENT ON COLUMN task_steps.is_deleted IS '逻辑删除标记';

COMMENT ON TABLE task_traces IS '任务追踪事件表';
COMMENT ON COLUMN task_traces.id IS '主键ID';
COMMENT ON COLUMN task_traces.task_id IS '任务ID';
COMMENT ON COLUMN task_traces.trace_id IS '任务追踪ID';
COMMENT ON COLUMN task_traces.task_step_id IS '关联步骤ID';
COMMENT ON COLUMN task_traces.event_type IS '事件类型';
COMMENT ON COLUMN task_traces.span_name IS '追踪名称';
COMMENT ON COLUMN task_traces.trace_status IS '追踪状态';
COMMENT ON COLUMN task_traces.input_snapshot IS '输入快照';
COMMENT ON COLUMN task_traces.output_snapshot IS '输出快照';
COMMENT ON COLUMN task_traces.error_message IS '错误信息';
COMMENT ON COLUMN task_traces.started_at IS '开始时间';
COMMENT ON COLUMN task_traces.finished_at IS '结束时间';
COMMENT ON COLUMN task_traces.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN task_traces.metadata IS '扩展元数据';
COMMENT ON COLUMN task_traces.created_at IS '创建时间';
COMMENT ON COLUMN task_traces.created_by IS '创建人';
COMMENT ON COLUMN task_traces.updated_at IS '更新时间';
COMMENT ON COLUMN task_traces.updated_by IS '更新人';
COMMENT ON COLUMN task_traces.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS eval_suites (
    id BIGSERIAL PRIMARY KEY,
    suite_code VARCHAR(64) NOT NULL,
    suite_name VARCHAR(128) NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_suites_code_deleted ON eval_suites (suite_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eval_suites_agent_id ON eval_suites (agent_id);
CREATE INDEX IF NOT EXISTS idx_eval_suites_status ON eval_suites (status);

COMMENT ON TABLE eval_suites IS 'Eval Suite 定义表';
COMMENT ON COLUMN eval_suites.id IS '主键ID';
COMMENT ON COLUMN eval_suites.suite_code IS 'Suite 编码';
COMMENT ON COLUMN eval_suites.suite_name IS 'Suite 名称';
COMMENT ON COLUMN eval_suites.agent_id IS '绑定 Agent ID';
COMMENT ON COLUMN eval_suites.agent_name IS '绑定 Agent 名称快照';
COMMENT ON COLUMN eval_suites.description IS '描述';
COMMENT ON COLUMN eval_suites.status IS '状态';
COMMENT ON COLUMN eval_suites.metadata IS '扩展信息';
COMMENT ON COLUMN eval_suites.current_version IS '当前版本号';
COMMENT ON COLUMN eval_suites.created_at IS '创建时间';
COMMENT ON COLUMN eval_suites.created_by IS '创建人';
COMMENT ON COLUMN eval_suites.updated_at IS '更新时间';
COMMENT ON COLUMN eval_suites.updated_by IS '更新人';
COMMENT ON COLUMN eval_suites.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS eval_cases (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    case_code VARCHAR(64) NOT NULL,
    case_name VARCHAR(128) NOT NULL,
    user_input TEXT NOT NULL,
    expected_reply_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    forbidden_reply_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_tool_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_step_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_final_status VARCHAR(32),
    pass_threshold INTEGER NOT NULL DEFAULT 80,
    order_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_cases_suite_code_deleted ON eval_cases (suite_id, case_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eval_cases_suite_id ON eval_cases (suite_id);
CREATE INDEX IF NOT EXISTS idx_eval_cases_status ON eval_cases (status);

COMMENT ON TABLE eval_cases IS 'Eval 用例定义表';
COMMENT ON COLUMN eval_cases.id IS '主键ID';
COMMENT ON COLUMN eval_cases.suite_id IS '所属 Suite ID';
COMMENT ON COLUMN eval_cases.case_code IS '用例编码';
COMMENT ON COLUMN eval_cases.case_name IS '用例名称';
COMMENT ON COLUMN eval_cases.user_input IS '评测输入';
COMMENT ON COLUMN eval_cases.expected_reply_keywords IS '期望回复关键词';
COMMENT ON COLUMN eval_cases.forbidden_reply_keywords IS '禁止回复关键词';
COMMENT ON COLUMN eval_cases.expected_tool_codes IS '期望 Tool 编码列表';
COMMENT ON COLUMN eval_cases.expected_step_types IS '期望步骤类型列表';
COMMENT ON COLUMN eval_cases.expected_final_status IS '期望最终状态';
COMMENT ON COLUMN eval_cases.pass_threshold IS '通过阈值';
COMMENT ON COLUMN eval_cases.order_no IS '排序号';
COMMENT ON COLUMN eval_cases.status IS '状态';
COMMENT ON COLUMN eval_cases.metadata IS '扩展信息';
COMMENT ON COLUMN eval_cases.created_at IS '创建时间';
COMMENT ON COLUMN eval_cases.created_by IS '创建人';
COMMENT ON COLUMN eval_cases.updated_at IS '更新时间';
COMMENT ON COLUMN eval_cases.updated_by IS '更新人';
COMMENT ON COLUMN eval_cases.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS eval_runs (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    suite_version INTEGER NOT NULL DEFAULT 1,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    run_code VARCHAR(64) NOT NULL,
    run_status VARCHAR(32) NOT NULL DEFAULT 'running',
    total_cases INTEGER NOT NULL DEFAULT 0,
    passed_cases INTEGER NOT NULL DEFAULT 0,
    failed_cases INTEGER NOT NULL DEFAULT 0,
    average_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'manual',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_runs_code_deleted ON eval_runs (run_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eval_runs_suite_id ON eval_runs (suite_id);
CREATE INDEX IF NOT EXISTS idx_eval_runs_agent_id ON eval_runs (agent_id);
CREATE INDEX IF NOT EXISTS idx_eval_runs_status ON eval_runs (run_status);
CREATE INDEX IF NOT EXISTS idx_eval_runs_started_at ON eval_runs (started_at);

COMMENT ON TABLE eval_runs IS 'Eval 执行批次表';
COMMENT ON COLUMN eval_runs.id IS '主键ID';
COMMENT ON COLUMN eval_runs.suite_id IS 'Suite ID';
COMMENT ON COLUMN eval_runs.suite_version IS '执行时命中的 Suite 版本';
COMMENT ON COLUMN eval_runs.agent_id IS '执行 Agent ID';
COMMENT ON COLUMN eval_runs.agent_name IS '执行 Agent 名称快照';
COMMENT ON COLUMN eval_runs.run_code IS '运行编码';
COMMENT ON COLUMN eval_runs.run_status IS '运行状态';
COMMENT ON COLUMN eval_runs.total_cases IS '总用例数';
COMMENT ON COLUMN eval_runs.passed_cases IS '通过用例数';
COMMENT ON COLUMN eval_runs.failed_cases IS '失败用例数';
COMMENT ON COLUMN eval_runs.average_score IS '平均分';
COMMENT ON COLUMN eval_runs.trigger_type IS '触发方式';
COMMENT ON COLUMN eval_runs.metadata IS '扩展信息';
COMMENT ON COLUMN eval_runs.started_at IS '开始时间';
COMMENT ON COLUMN eval_runs.finished_at IS '结束时间';
COMMENT ON COLUMN eval_runs.created_at IS '创建时间';
COMMENT ON COLUMN eval_runs.created_by IS '创建人';
COMMENT ON COLUMN eval_runs.updated_at IS '更新时间';
COMMENT ON COLUMN eval_runs.updated_by IS '更新人';
COMMENT ON COLUMN eval_runs.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS eval_run_cases (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    conversation_id BIGINT,
    task_id BIGINT,
    trace_id VARCHAR(64),
    run_status VARCHAR(32) NOT NULL DEFAULT 'running',
    passed BOOLEAN,
    score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    actual_reply TEXT,
    actual_tool_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    actual_step_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    actual_final_status VARCHAR(32),
    failure_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_eval_run_cases_run_id ON eval_run_cases (run_id);
CREATE INDEX IF NOT EXISTS idx_eval_run_cases_case_id ON eval_run_cases (case_id);
CREATE INDEX IF NOT EXISTS idx_eval_run_cases_task_id ON eval_run_cases (task_id);
CREATE INDEX IF NOT EXISTS idx_eval_run_cases_status ON eval_run_cases (run_status);

COMMENT ON TABLE eval_run_cases IS 'Eval 执行用例结果表';
COMMENT ON COLUMN eval_run_cases.id IS '主键ID';
COMMENT ON COLUMN eval_run_cases.run_id IS '所属 Run ID';
COMMENT ON COLUMN eval_run_cases.case_id IS '用例 ID';
COMMENT ON COLUMN eval_run_cases.conversation_id IS '执行会话 ID';
COMMENT ON COLUMN eval_run_cases.task_id IS '执行任务 ID';
COMMENT ON COLUMN eval_run_cases.trace_id IS '执行 Trace ID';
COMMENT ON COLUMN eval_run_cases.run_status IS '执行状态';
COMMENT ON COLUMN eval_run_cases.passed IS '是否通过';
COMMENT ON COLUMN eval_run_cases.score IS '评分';
COMMENT ON COLUMN eval_run_cases.actual_reply IS '实际回复';
COMMENT ON COLUMN eval_run_cases.actual_tool_codes IS '实际 Tool 编码列表';
COMMENT ON COLUMN eval_run_cases.actual_step_types IS '实际步骤类型列表';
COMMENT ON COLUMN eval_run_cases.actual_final_status IS '实际最终状态';
COMMENT ON COLUMN eval_run_cases.failure_reasons IS '失败原因列表';
COMMENT ON COLUMN eval_run_cases.metadata IS '扩展信息';
COMMENT ON COLUMN eval_run_cases.started_at IS '开始时间';
COMMENT ON COLUMN eval_run_cases.finished_at IS '结束时间';
COMMENT ON COLUMN eval_run_cases.created_at IS '创建时间';
COMMENT ON COLUMN eval_run_cases.created_by IS '创建人';
COMMENT ON COLUMN eval_run_cases.updated_at IS '更新时间';
COMMENT ON COLUMN eval_run_cases.updated_by IS '更新人';
COMMENT ON COLUMN eval_run_cases.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS eval_suite_versions (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    suite_code VARCHAR(64) NOT NULL,
    suite_name VARCHAR(128) NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    cases_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_suite_versions_suite_version_deleted
    ON eval_suite_versions (suite_id, version_no, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eval_suite_versions_suite_id
    ON eval_suite_versions (suite_id);

COMMENT ON TABLE eval_suite_versions IS 'Eval Suite 版本快照表';
COMMENT ON COLUMN eval_suite_versions.id IS '主键ID';
COMMENT ON COLUMN eval_suite_versions.suite_id IS 'Suite ID';
COMMENT ON COLUMN eval_suite_versions.version_no IS '版本号';
COMMENT ON COLUMN eval_suite_versions.suite_code IS 'Suite 编码';
COMMENT ON COLUMN eval_suite_versions.suite_name IS 'Suite 名称';
COMMENT ON COLUMN eval_suite_versions.agent_id IS 'Agent ID';
COMMENT ON COLUMN eval_suite_versions.agent_name IS 'Agent 名称快照';
COMMENT ON COLUMN eval_suite_versions.description IS '描述';
COMMENT ON COLUMN eval_suite_versions.status IS '状态';
COMMENT ON COLUMN eval_suite_versions.metadata IS '扩展信息';
COMMENT ON COLUMN eval_suite_versions.cases_json IS '用例快照 JSON';
COMMENT ON COLUMN eval_suite_versions.created_at IS '创建时间';
COMMENT ON COLUMN eval_suite_versions.created_by IS '创建人';
COMMENT ON COLUMN eval_suite_versions.updated_at IS '更新时间';
COMMENT ON COLUMN eval_suite_versions.updated_by IS '更新人';
COMMENT ON COLUMN eval_suite_versions.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS task_plans (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    plan_version INTEGER NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    assumptions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    constraints_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    planner_prompt_id BIGINT,
    planner_prompt_name VARCHAR(128),
    source VARCHAR(32) NOT NULL DEFAULT 'planner',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_plans_task_version_deleted ON task_plans (task_id, plan_version, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_plans_plan_id_deleted ON task_plans (plan_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_task_plans_task_id ON task_plans (task_id);
CREATE INDEX IF NOT EXISTS idx_task_plans_status ON task_plans (status);

COMMENT ON TABLE task_plans IS '任务计划表';
COMMENT ON COLUMN task_plans.id IS '主键ID';
COMMENT ON COLUMN task_plans.task_id IS '任务ID';
COMMENT ON COLUMN task_plans.plan_version IS '计划版本号，从1递增';
COMMENT ON COLUMN task_plans.plan_id IS '计划业务ID';
COMMENT ON COLUMN task_plans.plan_type IS '计划类型';
COMMENT ON COLUMN task_plans.goal IS '计划目标';
COMMENT ON COLUMN task_plans.status IS '计划状态';
COMMENT ON COLUMN task_plans.assumptions_json IS '规划假设列表';
COMMENT ON COLUMN task_plans.constraints_json IS '规划约束列表';
COMMENT ON COLUMN task_plans.risks_json IS '规划风险列表';
COMMENT ON COLUMN task_plans.planner_prompt_id IS '规划Prompt ID快照';
COMMENT ON COLUMN task_plans.planner_prompt_name IS '规划Prompt名称快照';
COMMENT ON COLUMN task_plans.source IS '计划来源，如planner、fallback、resume、approval_resume';
COMMENT ON COLUMN task_plans.metadata IS '扩展信息';
COMMENT ON COLUMN task_plans.created_at IS '创建时间';
COMMENT ON COLUMN task_plans.created_by IS '创建人';
COMMENT ON COLUMN task_plans.updated_at IS '更新时间';
COMMENT ON COLUMN task_plans.updated_by IS '更新人';
COMMENT ON COLUMN task_plans.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS task_plan_steps (
    id BIGSERIAL PRIMARY KEY,
    task_plan_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    plan_version INTEGER NOT NULL,
    step_id VARCHAR(64) NOT NULL,
    step_no INTEGER NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    objective TEXT,
    depends_on_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_tool_code VARCHAR(64),
    target_workflow_id BIGINT,
    inputs_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_output TEXT,
    failure_policy VARCHAR(32),
    replannable BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    description TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_plan_steps_plan_step_deleted ON task_plan_steps (task_plan_id, step_no, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_plan_steps_plan_step_id_deleted ON task_plan_steps (task_plan_id, step_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_task_plan_steps_task_id ON task_plan_steps (task_id);
CREATE INDEX IF NOT EXISTS idx_task_plan_steps_plan_version ON task_plan_steps (task_id, plan_version);
CREATE INDEX IF NOT EXISTS idx_task_plan_steps_status ON task_plan_steps (status);

COMMENT ON TABLE task_plan_steps IS '任务计划步骤表';
COMMENT ON COLUMN task_plan_steps.id IS '主键ID';
COMMENT ON COLUMN task_plan_steps.task_plan_id IS '任务计划ID';
COMMENT ON COLUMN task_plan_steps.task_id IS '任务ID';
COMMENT ON COLUMN task_plan_steps.plan_version IS '计划版本号';
COMMENT ON COLUMN task_plan_steps.step_id IS '步骤业务ID';
COMMENT ON COLUMN task_plan_steps.step_no IS '步骤序号';
COMMENT ON COLUMN task_plan_steps.step_type IS '步骤类型';
COMMENT ON COLUMN task_plan_steps.step_name IS '步骤名称';
COMMENT ON COLUMN task_plan_steps.objective IS '步骤目标';
COMMENT ON COLUMN task_plan_steps.depends_on_json IS '依赖步骤ID列表';
COMMENT ON COLUMN task_plan_steps.target_tool_code IS '目标Tool编码';
COMMENT ON COLUMN task_plan_steps.target_workflow_id IS '目标Workflow ID';
COMMENT ON COLUMN task_plan_steps.inputs_json IS '步骤输入';
COMMENT ON COLUMN task_plan_steps.expected_output IS '期望输出';
COMMENT ON COLUMN task_plan_steps.failure_policy IS '失败策略';
COMMENT ON COLUMN task_plan_steps.replannable IS '是否允许重规划';
COMMENT ON COLUMN task_plan_steps.status IS '步骤计划状态';
COMMENT ON COLUMN task_plan_steps.description IS '步骤说明';
COMMENT ON COLUMN task_plan_steps.metadata IS '扩展信息';
COMMENT ON COLUMN task_plan_steps.created_at IS '创建时间';
COMMENT ON COLUMN task_plan_steps.created_by IS '创建人';
COMMENT ON COLUMN task_plan_steps.updated_at IS '更新时间';
COMMENT ON COLUMN task_plan_steps.updated_by IS '更新人';
COMMENT ON COLUMN task_plan_steps.is_deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS approvals (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_step_id BIGINT NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    approval_comment VARCHAR(500),
    approved_by BIGINT,
    approved_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_approvals_conversation_id ON approvals (conversation_id);
CREATE INDEX IF NOT EXISTS idx_approvals_task_id ON approvals (task_id);
CREATE INDEX IF NOT EXISTS idx_approvals_task_step_id ON approvals (task_step_id);
CREATE INDEX IF NOT EXISTS idx_approvals_status ON approvals (status);
CREATE INDEX IF NOT EXISTS idx_approvals_created_at ON approvals (created_at);
CREATE INDEX IF NOT EXISTS idx_approvals_approved_at ON approvals (approved_at);

COMMENT ON TABLE approvals IS '审批记录表';
COMMENT ON COLUMN approvals.id IS '主键ID';
COMMENT ON COLUMN approvals.conversation_id IS '会话ID';
COMMENT ON COLUMN approvals.task_id IS '任务ID';
COMMENT ON COLUMN approvals.task_step_id IS '任务步骤ID';
COMMENT ON COLUMN approvals.node_id IS '审批节点ID';
COMMENT ON COLUMN approvals.node_name IS '审批节点名称';
COMMENT ON COLUMN approvals.status IS '审批状态';
COMMENT ON COLUMN approvals.approval_comment IS '审批说明';
COMMENT ON COLUMN approvals.approved_by IS '审批人';
COMMENT ON COLUMN approvals.approved_at IS '审批时间';
COMMENT ON COLUMN approvals.metadata IS '扩展信息';
COMMENT ON COLUMN approvals.created_at IS '创建时间';
COMMENT ON COLUMN approvals.created_by IS '创建人';
COMMENT ON COLUMN approvals.updated_at IS '更新时间';
COMMENT ON COLUMN approvals.updated_by IS '更新人';
COMMENT ON COLUMN approvals.is_deleted IS '逻辑删除标记';
