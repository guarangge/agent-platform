CREATE TABLE IF NOT EXISTS eval_suites (
    id BIGSERIAL PRIMARY KEY,
    suite_code VARCHAR(64) NOT NULL,
    suite_name VARCHAR(128) NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
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
