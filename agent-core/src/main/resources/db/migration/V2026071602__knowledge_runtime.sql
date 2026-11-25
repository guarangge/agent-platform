CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_runtime_bases (
    id BIGSERIAL PRIMARY KEY,
    registry_knowledge_id BIGINT NOT NULL,
    knowledge_code VARCHAR(64) NOT NULL,
    knowledge_name VARCHAR(128) NOT NULL,
    embedding_model_code VARCHAR(64),
    chunk_size INTEGER NOT NULL,
    chunk_overlap INTEGER NOT NULL,
    retrieval_top_k INTEGER NOT NULL,
    similarity_threshold DOUBLE PRECISION NOT NULL,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    registry_updated_at TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_knowledge_registry_id ON knowledge_runtime_bases (registry_knowledge_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_knowledge_code ON knowledge_runtime_bases (knowledge_code);

CREATE TABLE IF NOT EXISTS knowledge_runtime_documents (
    id BIGSERIAL PRIMARY KEY,
    runtime_knowledge_id BIGINT NOT NULL,
    registry_document_id BIGINT NOT NULL,
    document_code VARCHAR(128) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    source_uri VARCHAR(1000),
    content_hash VARCHAR(64) NOT NULL,
    document_version INTEGER NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    indexed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_runtime_documents_base FOREIGN KEY (runtime_knowledge_id) REFERENCES knowledge_runtime_bases (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_documents_registry_id ON knowledge_runtime_documents (registry_document_id);
CREATE INDEX IF NOT EXISTS idx_runtime_documents_base ON knowledge_runtime_documents (runtime_knowledge_id);

CREATE TABLE IF NOT EXISTS knowledge_runtime_chunks (
    id BIGSERIAL PRIMARY KEY,
    runtime_knowledge_id BIGINT NOT NULL,
    runtime_document_id BIGINT NOT NULL,
    chunk_no INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    token_estimate INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(chunk_text, ''))) STORED,
    embedding VECTOR(384),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_runtime_chunks_base FOREIGN KEY (runtime_knowledge_id) REFERENCES knowledge_runtime_bases (id) ON DELETE CASCADE,
    CONSTRAINT fk_runtime_chunks_document FOREIGN KEY (runtime_document_id) REFERENCES knowledge_runtime_documents (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_chunks_document_no ON knowledge_runtime_chunks (runtime_document_id, chunk_no);
CREATE INDEX IF NOT EXISTS idx_runtime_chunks_base ON knowledge_runtime_chunks (runtime_knowledge_id);
CREATE INDEX IF NOT EXISTS idx_runtime_chunks_search ON knowledge_runtime_chunks USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_runtime_chunks_embedding ON knowledge_runtime_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS knowledge_runtime_index_tasks (
    id BIGSERIAL PRIMARY KEY,
    registry_task_id BIGINT NOT NULL,
    task_code VARCHAR(64) NOT NULL,
    registry_knowledge_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_documents INTEGER NOT NULL DEFAULT 0,
    processed_documents INTEGER NOT NULL DEFAULT 0,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_index_registry_task ON knowledge_runtime_index_tasks (registry_task_id);
CREATE INDEX IF NOT EXISTS idx_runtime_index_status ON knowledge_runtime_index_tasks (status, created_at);

CREATE TABLE IF NOT EXISTS knowledge_retrieval_logs (
    id BIGSERIAL PRIMARY KEY,
    retrieval_code VARCHAR(64) NOT NULL,
    user_id BIGINT,
    agent_id BIGINT,
    conversation_id BIGINT,
    task_id BIGINT,
    trace_id VARCHAR(64),
    query_text TEXT NOT NULL,
    knowledge_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    top_k INTEGER NOT NULL,
    hit_count INTEGER NOT NULL,
    latency_ms BIGINT NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_retrieval_code ON knowledge_retrieval_logs (retrieval_code);
CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_agent_time ON knowledge_retrieval_logs (agent_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_trace ON knowledge_retrieval_logs (trace_id);

CREATE TABLE IF NOT EXISTS knowledge_eval_runs (
    id BIGSERIAL PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL,
    agent_id BIGINT NOT NULL,
    knowledge_id BIGINT,
    case_count INTEGER NOT NULL,
    hit_case_count INTEGER NOT NULL,
    recall_at_k DOUBLE PRECISION NOT NULL,
    mean_reciprocal_rank DOUBLE PRECISION NOT NULL,
    average_latency_ms DOUBLE PRECISION NOT NULL,
    cases JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_eval_run_code ON knowledge_eval_runs (run_code);
CREATE INDEX IF NOT EXISTS idx_knowledge_eval_agent_time ON knowledge_eval_runs (agent_id, created_at DESC);

COMMENT ON TABLE knowledge_runtime_bases IS '运行时知识库配置快照';
COMMENT ON TABLE knowledge_runtime_documents IS '运行时已索引文档快照';
COMMENT ON TABLE knowledge_runtime_chunks IS '运行时知识分块和向量索引';
COMMENT ON TABLE knowledge_runtime_index_tasks IS '运行时索引任务执行记录';
COMMENT ON TABLE knowledge_retrieval_logs IS '知识检索与引用审计日志';
COMMENT ON TABLE knowledge_eval_runs IS '知识检索评测运行记录';
COMMENT ON COLUMN knowledge_runtime_chunks.embedding IS '384 维检索向量，兼容 pgvector 余弦检索';
COMMENT ON COLUMN knowledge_retrieval_logs.citations IS '本次检索返回的文档、分块、得分和来源引用';
COMMENT ON COLUMN knowledge_runtime_bases.registry_knowledge_id IS '注册中心知识库ID';
COMMENT ON COLUMN knowledge_runtime_bases.knowledge_code IS '知识库编码快照';
COMMENT ON COLUMN knowledge_runtime_bases.knowledge_name IS '知识库名称快照';
COMMENT ON COLUMN knowledge_runtime_bases.config_snapshot IS '运行时配置快照';
COMMENT ON COLUMN knowledge_runtime_bases.synced_at IS '最近同步时间';
COMMENT ON COLUMN knowledge_runtime_documents.runtime_knowledge_id IS '运行时知识库ID';
COMMENT ON COLUMN knowledge_runtime_documents.registry_document_id IS '注册中心文档ID';
COMMENT ON COLUMN knowledge_runtime_documents.document_code IS '文档编码快照';
COMMENT ON COLUMN knowledge_runtime_documents.document_name IS '文档名称快照';
COMMENT ON COLUMN knowledge_runtime_documents.content_hash IS '原文SHA-256';
COMMENT ON COLUMN knowledge_runtime_documents.document_version IS '文档版本';
COMMENT ON COLUMN knowledge_runtime_chunks.runtime_knowledge_id IS '运行时知识库ID';
COMMENT ON COLUMN knowledge_runtime_chunks.runtime_document_id IS '运行时文档ID';
COMMENT ON COLUMN knowledge_runtime_chunks.chunk_no IS '文档内分块序号';
COMMENT ON COLUMN knowledge_runtime_chunks.chunk_text IS '分块文本';
COMMENT ON COLUMN knowledge_runtime_chunks.token_estimate IS '估算Token数';
COMMENT ON COLUMN knowledge_runtime_chunks.search_vector IS '全文检索向量';
COMMENT ON COLUMN knowledge_runtime_index_tasks.registry_task_id IS '注册中心索引任务ID';
COMMENT ON COLUMN knowledge_runtime_index_tasks.task_code IS '索引任务编码';
COMMENT ON COLUMN knowledge_runtime_index_tasks.status IS '运行时索引状态';
COMMENT ON COLUMN knowledge_retrieval_logs.retrieval_code IS '检索编码';
COMMENT ON COLUMN knowledge_retrieval_logs.user_id IS '请求用户ID';
COMMENT ON COLUMN knowledge_retrieval_logs.agent_id IS '执行Agent ID';
COMMENT ON COLUMN knowledge_retrieval_logs.query_text IS '检索问题';
COMMENT ON COLUMN knowledge_retrieval_logs.knowledge_ids IS '实际检索的知识库ID集合';
COMMENT ON COLUMN knowledge_retrieval_logs.top_k IS '召回数量上限';
COMMENT ON COLUMN knowledge_retrieval_logs.hit_count IS '实际命中数';
COMMENT ON COLUMN knowledge_retrieval_logs.latency_ms IS '检索耗时毫秒';
COMMENT ON COLUMN knowledge_eval_runs.run_code IS '检索评测编码';
COMMENT ON COLUMN knowledge_eval_runs.agent_id IS '评测Agent ID';
COMMENT ON COLUMN knowledge_eval_runs.knowledge_id IS '指定知识库ID';
COMMENT ON COLUMN knowledge_eval_runs.case_count IS '评测用例数';
COMMENT ON COLUMN knowledge_eval_runs.hit_case_count IS '命中用例数';
COMMENT ON COLUMN knowledge_eval_runs.recall_at_k IS 'Recall@K';
COMMENT ON COLUMN knowledge_eval_runs.mean_reciprocal_rank IS '平均倒数排名MRR';
COMMENT ON COLUMN knowledge_eval_runs.average_latency_ms IS '平均检索耗时毫秒';
