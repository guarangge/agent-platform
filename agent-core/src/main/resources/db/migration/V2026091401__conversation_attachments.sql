CREATE TABLE IF NOT EXISTS conversation_attachments (
    id BIGSERIAL PRIMARY KEY,
    attachment_code VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL,
    message_id BIGINT,
    run_code VARCHAR(64),
    direction VARCHAR(16) NOT NULL DEFAULT 'input',
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ready',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_conversation_attachments_direction CHECK (direction IN ('input', 'output')),
    CONSTRAINT ck_conversation_attachments_status CHECK (status IN ('uploading', 'ready', 'failed', 'deleted')),
    CONSTRAINT ck_conversation_attachments_size CHECK (size_bytes > 0),
    CONSTRAINT fk_conversation_attachments_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_conversation_attachments_message FOREIGN KEY (message_id) REFERENCES messages(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_attachments_code ON conversation_attachments (attachment_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_attachments_storage_key ON conversation_attachments (storage_provider, storage_key);
CREATE INDEX IF NOT EXISTS idx_conversation_attachments_conversation ON conversation_attachments (conversation_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_conversation_attachments_message ON conversation_attachments (message_id) WHERE message_id IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_conversation_attachments_run_code ON conversation_attachments (run_code) WHERE run_code IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_conversation_attachments_scope ON conversation_attachments (tenant_id, project_id);
CREATE INDEX IF NOT EXISTS idx_conversation_attachments_sha256 ON conversation_attachments (sha256);

ALTER TABLE conversation_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_attachments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON conversation_attachments
    USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0
        OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
    WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0
        OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));

COMMENT ON TABLE conversation_attachments IS '会话输入输出附件元数据，文件正文保存于外部存储';
COMMENT ON COLUMN conversation_attachments.attachment_code IS '稳定附件编码';
COMMENT ON COLUMN conversation_attachments.conversation_id IS '所属会话ID';
COMMENT ON COLUMN conversation_attachments.message_id IS '关联消息ID，上传后发送消息时绑定';
COMMENT ON COLUMN conversation_attachments.run_code IS '关联异步Run编码';
COMMENT ON COLUMN conversation_attachments.direction IS '附件方向：input/output';
COMMENT ON COLUMN conversation_attachments.original_filename IS 'UTF-8原始文件名';
COMMENT ON COLUMN conversation_attachments.mime_type IS '按文件签名识别的媒体类型';
COMMENT ON COLUMN conversation_attachments.size_bytes IS '文件字节数';
COMMENT ON COLUMN conversation_attachments.sha256 IS '文件正文SHA-256';
COMMENT ON COLUMN conversation_attachments.storage_provider IS '存储实现编码：local/minio/s3';
COMMENT ON COLUMN conversation_attachments.storage_key IS '存储内部键，不作为公开下载URL';
COMMENT ON COLUMN conversation_attachments.status IS 'uploading/ready/failed/deleted';
COMMENT ON COLUMN conversation_attachments.metadata IS '不包含文件正文的扩展元数据';

