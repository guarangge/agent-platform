ALTER TABLE tasks DROP COLUMN IF EXISTS workflow_id;
ALTER TABLE tasks DROP COLUMN IF EXISTS workflow_name;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS execution_type VARCHAR(32);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS current_plan_version INTEGER;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS execution_chain_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS planner_prompt_id BIGINT;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS planner_prompt_name VARCHAR(128);

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
CREATE INDEX IF NOT EXISTS idx_task_traces_started_at ON task_traces (started_at);

COMMENT ON TABLE tasks IS '执行任务表';
COMMENT ON COLUMN tasks.id IS '主键ID';
COMMENT ON COLUMN tasks.task_code IS '任务编码，唯一';
COMMENT ON COLUMN tasks.trace_id IS '本次任务执行追踪ID';
COMMENT ON COLUMN tasks.conversation_id IS '所属会话ID';
COMMENT ON COLUMN tasks.message_id IS '触发任务的用户消息ID';
COMMENT ON COLUMN tasks.agent_id IS '执行任务的Agent ID';
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
