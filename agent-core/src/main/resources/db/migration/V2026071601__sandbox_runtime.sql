CREATE TABLE sandbox_profiles (
    id BIGSERIAL PRIMARY KEY,
    profile_code VARCHAR(64) NOT NULL,
    profile_name VARCHAR(128) NOT NULL,
    image VARCHAR(255) NOT NULL,
    allowed_commands JSONB NOT NULL DEFAULT '[]'::jsonb,
    network_mode VARCHAR(32) NOT NULL DEFAULT 'none',
    memory_bytes BIGINT NOT NULL DEFAULT 268435456,
    nano_cpus BIGINT NOT NULL DEFAULT 1000000000,
    pids_limit BIGINT NOT NULL DEFAULT 128,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    max_output_bytes BIGINT NOT NULL DEFAULT 1048576,
    max_workspace_bytes BIGINT NOT NULL DEFAULT 10485760,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_sandbox_profile_network CHECK (network_mode IN ('none', 'bridge')),
    CONSTRAINT ck_sandbox_profile_limits CHECK (
        memory_bytes BETWEEN 67108864 AND 4294967296
        AND nano_cpus BETWEEN 100000000 AND 4000000000
        AND pids_limit BETWEEN 16 AND 1024
        AND timeout_seconds BETWEEN 1 AND 3600
        AND max_output_bytes BETWEEN 1024 AND 10485760
        AND max_workspace_bytes BETWEEN 1024 AND 104857600
    )
);

CREATE UNIQUE INDEX uk_sandbox_profiles_code_deleted ON sandbox_profiles (profile_code, is_deleted);
CREATE INDEX idx_sandbox_profiles_status ON sandbox_profiles (status);

CREATE TABLE sandbox_executions (
    id BIGSERIAL PRIMARY KEY,
    execution_code VARCHAR(64) NOT NULL UNIQUE,
    profile_id BIGINT NOT NULL REFERENCES sandbox_profiles (id),
    profile_code VARCHAR(64) NOT NULL,
    image VARCHAR(255) NOT NULL,
    command_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    container_id VARCHAR(128),
    exit_code INTEGER,
    timed_out BOOLEAN NOT NULL DEFAULT FALSE,
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    stdout_text TEXT,
    stderr_text TEXT,
    error_message TEXT,
    input_bytes BIGINT NOT NULL DEFAULT 0,
    output_bytes BIGINT NOT NULL DEFAULT 0,
    workspace_bytes BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    requested_by BIGINT,
    requested_username VARCHAR(128),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sandbox_execution_status CHECK (status IN ('pending', 'running', 'succeeded', 'failed', 'timed_out', 'cancelled'))
);

CREATE INDEX idx_sandbox_executions_profile_id ON sandbox_executions (profile_id);
CREATE INDEX idx_sandbox_executions_status_created ON sandbox_executions (status, created_at DESC);
CREATE INDEX idx_sandbox_executions_requester ON sandbox_executions (requested_by, created_at DESC);

COMMENT ON TABLE sandbox_profiles IS 'Sandbox 容器执行安全配置表';
COMMENT ON COLUMN sandbox_profiles.id IS '主键ID';
COMMENT ON COLUMN sandbox_profiles.profile_code IS '配置编码';
COMMENT ON COLUMN sandbox_profiles.profile_name IS '配置名称';
COMMENT ON COLUMN sandbox_profiles.image IS '容器镜像';
COMMENT ON COLUMN sandbox_profiles.allowed_commands IS '允许执行的命令白名单';
COMMENT ON COLUMN sandbox_profiles.network_mode IS '容器网络模式，仅允许 none 或 bridge';
COMMENT ON COLUMN sandbox_profiles.memory_bytes IS '容器内存上限，单位字节';
COMMENT ON COLUMN sandbox_profiles.nano_cpus IS '容器 CPU 上限，单位 NanoCPU';
COMMENT ON COLUMN sandbox_profiles.pids_limit IS '容器进程数上限';
COMMENT ON COLUMN sandbox_profiles.timeout_seconds IS '单次执行超时时间，单位秒';
COMMENT ON COLUMN sandbox_profiles.max_output_bytes IS '标准输出和错误输出总上限，单位字节';
COMMENT ON COLUMN sandbox_profiles.max_workspace_bytes IS '工作区输入输出总上限，单位字节';
COMMENT ON COLUMN sandbox_profiles.status IS '配置状态';
COMMENT ON COLUMN sandbox_profiles.created_at IS '创建时间';
COMMENT ON COLUMN sandbox_profiles.created_by IS '创建人';
COMMENT ON COLUMN sandbox_profiles.updated_at IS '更新时间';
COMMENT ON COLUMN sandbox_profiles.updated_by IS '更新人';
COMMENT ON COLUMN sandbox_profiles.is_deleted IS '逻辑删除标记';

COMMENT ON TABLE sandbox_executions IS 'Sandbox 容器执行审计表';
COMMENT ON COLUMN sandbox_executions.id IS '主键ID';
COMMENT ON COLUMN sandbox_executions.execution_code IS '执行编码';
COMMENT ON COLUMN sandbox_executions.profile_id IS 'Sandbox 配置ID';
COMMENT ON COLUMN sandbox_executions.profile_code IS '配置编码快照';
COMMENT ON COLUMN sandbox_executions.image IS '执行镜像快照';
COMMENT ON COLUMN sandbox_executions.command_json IS '命令参数快照';
COMMENT ON COLUMN sandbox_executions.status IS '执行状态';
COMMENT ON COLUMN sandbox_executions.container_id IS 'Docker 容器ID';
COMMENT ON COLUMN sandbox_executions.exit_code IS '容器退出码';
COMMENT ON COLUMN sandbox_executions.timed_out IS '是否执行超时';
COMMENT ON COLUMN sandbox_executions.output_truncated IS '输出是否被截断';
COMMENT ON COLUMN sandbox_executions.stdout_text IS '标准输出';
COMMENT ON COLUMN sandbox_executions.stderr_text IS '错误输出';
COMMENT ON COLUMN sandbox_executions.error_message IS '失败原因';
COMMENT ON COLUMN sandbox_executions.input_bytes IS '输入文件总字节数';
COMMENT ON COLUMN sandbox_executions.output_bytes IS '日志及产物总字节数';
COMMENT ON COLUMN sandbox_executions.workspace_bytes IS '执行结束时工作区字节数';
COMMENT ON COLUMN sandbox_executions.duration_ms IS '执行耗时，单位毫秒';
COMMENT ON COLUMN sandbox_executions.requested_by IS '请求用户ID';
COMMENT ON COLUMN sandbox_executions.requested_username IS '请求用户名快照';
COMMENT ON COLUMN sandbox_executions.started_at IS '开始时间';
COMMENT ON COLUMN sandbox_executions.finished_at IS '结束时间';
COMMENT ON COLUMN sandbox_executions.created_at IS '创建时间';
COMMENT ON COLUMN sandbox_executions.updated_at IS '更新时间';

INSERT INTO sandbox_profiles (
    profile_code, profile_name, image, allowed_commands, network_mode, memory_bytes, nano_cpus,
    pids_limit, timeout_seconds, max_output_bytes, max_workspace_bytes, status
) VALUES (
    'DEFAULT_ISOLATED', '默认强隔离配置', 'agent-sandbox-runtime:1.0.0', '["python3", "node", "jq"]'::jsonb,
    'none', 268435456, 1000000000, 128, 60, 1048576, 10485760, 'enabled'
) ON CONFLICT (profile_code, is_deleted) DO NOTHING;
