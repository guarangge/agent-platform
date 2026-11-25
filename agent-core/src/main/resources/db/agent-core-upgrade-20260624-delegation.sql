ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS root_task_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_task_step_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS task_kind VARCHAR(32) NOT NULL DEFAULT 'primary';

CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks (parent_task_id);
CREATE INDEX IF NOT EXISTS idx_tasks_root_task_id ON tasks (root_task_id);
CREATE INDEX IF NOT EXISTS idx_tasks_trigger_task_step_id ON tasks (trigger_task_step_id);
CREATE INDEX IF NOT EXISTS idx_tasks_task_kind ON tasks (task_kind);

COMMENT ON COLUMN tasks.parent_task_id IS '父任务ID';
COMMENT ON COLUMN tasks.root_task_id IS '根任务ID';
COMMENT ON COLUMN tasks.trigger_task_step_id IS '触发当前任务的父步骤ID';
COMMENT ON COLUMN tasks.task_kind IS '任务类型，如 primary、delegated';

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
