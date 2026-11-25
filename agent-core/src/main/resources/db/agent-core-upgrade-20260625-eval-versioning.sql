ALTER TABLE eval_suites
    ADD COLUMN IF NOT EXISTS current_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE eval_runs
    ADD COLUMN IF NOT EXISTS suite_version INTEGER NOT NULL DEFAULT 1;

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
