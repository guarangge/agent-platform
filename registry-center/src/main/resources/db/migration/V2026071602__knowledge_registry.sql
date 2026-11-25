CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    knowledge_code VARCHAR(64) NOT NULL,
    knowledge_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    embedding_model_code VARCHAR(64),
    chunk_size INTEGER NOT NULL DEFAULT 800,
    chunk_overlap INTEGER NOT NULL DEFAULT 120,
    retrieval_top_k INTEGER NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_code ON knowledge_bases (knowledge_code) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_status ON knowledge_bases (status) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS knowledge_sources (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_uri VARCHAR(1000),
    sync_mode VARCHAR(32) NOT NULL DEFAULT 'manual',
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    last_synced_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_knowledge_sources_base FOREIGN KEY (knowledge_id) REFERENCES knowledge_bases (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_sources_code ON knowledge_sources (knowledge_id, source_code) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_sources_base ON knowledge_sources (knowledge_id) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    source_id BIGINT,
    document_code VARCHAR(128) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'text/plain',
    source_uri VARCHAR(1000),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    indexed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_knowledge_documents_base FOREIGN KEY (knowledge_id) REFERENCES knowledge_bases (id),
    CONSTRAINT fk_knowledge_documents_source FOREIGN KEY (source_id) REFERENCES knowledge_sources (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_documents_code ON knowledge_documents (knowledge_id, document_code) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_base_status ON knowledge_documents (knowledge_id, status) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS knowledge_agent_rel (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_knowledge_agent_base FOREIGN KEY (knowledge_id) REFERENCES knowledge_bases (id),
    CONSTRAINT fk_knowledge_agent_agent FOREIGN KEY (agent_id) REFERENCES agents (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_agent_rel ON knowledge_agent_rel (knowledge_id, agent_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_agent_agent ON knowledge_agent_rel (agent_id, status) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS knowledge_role_rel (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_knowledge_role_base FOREIGN KEY (knowledge_id) REFERENCES knowledge_bases (id),
    CONSTRAINT fk_knowledge_role_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_role_rel ON knowledge_role_rel (knowledge_id, role_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_role_role ON knowledge_role_rel (role_id, status) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS knowledge_index_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_code VARCHAR(64) NOT NULL,
    knowledge_id BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'manual',
    requested_document_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    total_documents INTEGER NOT NULL DEFAULT 0,
    processed_documents INTEGER NOT NULL DEFAULT 0,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT fk_knowledge_index_tasks_base FOREIGN KEY (knowledge_id) REFERENCES knowledge_bases (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_index_tasks_code ON knowledge_index_tasks (task_code);
CREATE INDEX IF NOT EXISTS idx_knowledge_index_tasks_status ON knowledge_index_tasks (status, created_at);

COMMENT ON TABLE knowledge_bases IS '知识库定义表';
COMMENT ON TABLE knowledge_sources IS '知识来源配置表';
COMMENT ON TABLE knowledge_documents IS '知识文档原文与版本表';
COMMENT ON TABLE knowledge_agent_rel IS '知识库与 Agent 关联表';
COMMENT ON TABLE knowledge_role_rel IS '知识库角色访问权限表';
COMMENT ON TABLE knowledge_index_tasks IS '知识索引发布任务表';
COMMENT ON COLUMN knowledge_bases.embedding_model_code IS 'Embedding 模型编码，为空时运行时采用确定性向量降级';
COMMENT ON COLUMN knowledge_bases.chunk_size IS '分块目标字符数';
COMMENT ON COLUMN knowledge_bases.chunk_overlap IS '相邻分块重叠字符数';
COMMENT ON COLUMN knowledge_documents.content_hash IS '原文 SHA-256，用于增量索引判断';
COMMENT ON COLUMN knowledge_index_tasks.requested_document_ids IS '指定更新的文档 ID，为空数组时执行全量增量更新';
COMMENT ON COLUMN knowledge_bases.id IS '主键ID';
COMMENT ON COLUMN knowledge_bases.knowledge_code IS '知识库编码';
COMMENT ON COLUMN knowledge_bases.knowledge_name IS '知识库名称';
COMMENT ON COLUMN knowledge_bases.description IS '知识库描述';
COMMENT ON COLUMN knowledge_bases.retrieval_top_k IS '默认检索返回数量';
COMMENT ON COLUMN knowledge_bases.similarity_threshold IS '相似度阈值';
COMMENT ON COLUMN knowledge_bases.status IS '知识库状态';
COMMENT ON COLUMN knowledge_bases.metadata IS '扩展元数据';
COMMENT ON COLUMN knowledge_sources.knowledge_id IS '所属知识库ID';
COMMENT ON COLUMN knowledge_sources.source_code IS '来源编码';
COMMENT ON COLUMN knowledge_sources.source_name IS '来源名称';
COMMENT ON COLUMN knowledge_sources.source_type IS '来源类型';
COMMENT ON COLUMN knowledge_sources.source_uri IS '来源地址';
COMMENT ON COLUMN knowledge_sources.sync_mode IS '同步模式';
COMMENT ON COLUMN knowledge_sources.config IS '来源配置';
COMMENT ON COLUMN knowledge_sources.last_synced_at IS '最近同步时间';
COMMENT ON COLUMN knowledge_documents.knowledge_id IS '所属知识库ID';
COMMENT ON COLUMN knowledge_documents.source_id IS '知识来源ID';
COMMENT ON COLUMN knowledge_documents.document_code IS '文档编码';
COMMENT ON COLUMN knowledge_documents.document_name IS '文档名称';
COMMENT ON COLUMN knowledge_documents.content_type IS '内容MIME类型';
COMMENT ON COLUMN knowledge_documents.source_uri IS '文档来源地址';
COMMENT ON COLUMN knowledge_documents.content IS '文档原文';
COMMENT ON COLUMN knowledge_documents.version IS '文档版本号';
COMMENT ON COLUMN knowledge_documents.status IS '文档状态';
COMMENT ON COLUMN knowledge_documents.indexed_at IS '最近建索引时间';
COMMENT ON COLUMN knowledge_agent_rel.knowledge_id IS '知识库ID';
COMMENT ON COLUMN knowledge_agent_rel.agent_id IS 'Agent ID';
COMMENT ON COLUMN knowledge_agent_rel.priority IS '注入优先级';
COMMENT ON COLUMN knowledge_role_rel.knowledge_id IS '知识库ID';
COMMENT ON COLUMN knowledge_role_rel.role_id IS '有权访问的角色ID';
COMMENT ON COLUMN knowledge_index_tasks.task_code IS '索引任务编码';
COMMENT ON COLUMN knowledge_index_tasks.knowledge_id IS '知识库ID';
COMMENT ON COLUMN knowledge_index_tasks.trigger_type IS '任务触发类型';
COMMENT ON COLUMN knowledge_index_tasks.status IS '索引任务状态';
COMMENT ON COLUMN knowledge_index_tasks.total_documents IS '文档总数';
COMMENT ON COLUMN knowledge_index_tasks.processed_documents IS '已处理文档数';
COMMENT ON COLUMN knowledge_index_tasks.total_chunks IS '生成分块数';
COMMENT ON COLUMN knowledge_index_tasks.error_message IS '失败原因';
